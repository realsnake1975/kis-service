package dev.kis.dashboard;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.auth.KisAccount;
import dev.kis.openapi.auth.KisAccountContext;
import dev.kis.openapi.KisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Read-only view of the configured KIS account. This controller never places or changes orders. */
@RestController
public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    private final AccountBalanceSource source;
    private final KisProperties properties;
    private final UserAccountLookup accounts;

    public AccountController(AccountBalanceSource source, KisProperties properties, UserAccountLookup accounts) {
        this.source = source;
        this.properties = properties;
        this.accounts = accounts;
    }

    /** Balance of the signed-in user's own registered account; other users' accounts are never used. */
    @GetMapping("/api/account/balance")
    Mono<AccountBalance> balance(Authentication authentication) {
        String userKey = UserKeys.of(authentication);
        KisEnvironment environment = properties.getEnvironment();
        return Mono.fromCallable(() -> accounts.find(userKey, environment))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(found -> found.map(account -> fetch(account))
                        .orElseGet(() -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 KIS 계좌 정보가 없습니다."))));
    }

    private Mono<AccountBalance> fetch(KisAccount account) {
        return Mono.defer(source::balance)
                .contextWrite(KisAccountContext.with(account))
                .onErrorMap(IllegalStateException.class, e -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "KIS 계좌 정보가 올바르지 않습니다."))
                .onErrorMap(e -> !(e instanceof ResponseStatusException), e -> {
                    log.warn("KIS balance inquiry failed", e);
                    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "한국투자증권 잔고 조회에 실패했습니다.");
                })
                .map(response -> toBalance(response, account));
    }

    AccountBalance toBalance(KisApiResponse response, KisAccount account) {
        if (!response.isSuccess()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "한국투자증권 잔고 조회에 실패했습니다: " + (response.msg1() == null ? response.msgCd() : response.msg1().strip()));
        }
        List<Holding> holdings = new ArrayList<>();
        if (response.output1() != null) {
            for (JsonNode row : response.output1()) {
                BigDecimal quantity = number(row, "hldg_qty");
                if (quantity == null || quantity.signum() <= 0) continue; // KIS also lists positions sold out today
                holdings.add(new Holding(text(row, "pdno"), text(row, "prdt_name"), quantity,
                        number(row, "pchs_avg_pric"), number(row, "prpr"), number(row, "pchs_amt"),
                        number(row, "evlu_amt"), number(row, "evlu_pfls_amt"), number(row, "evlu_pfls_rt")));
            }
        }
        JsonNode summary = response.output2();
        if (summary != null && summary.isArray()) summary = summary.isEmpty() ? null : summary.get(0);
        BigDecimal purchase = number(summary, "pchs_amt_smtl_amt");
        BigDecimal profitLoss = number(summary, "evlu_pfls_smtl_amt");
        BigDecimal profitRate = purchase == null || profitLoss == null || purchase.signum() == 0 ? null
                : profitLoss.multiply(BigDecimal.valueOf(100)).divide(purchase, 2, RoundingMode.HALF_UP);
        return new AccountBalance(properties.getEnvironment().name(), DashboardController.maskAccount(account.accountNumber()),
                number(summary, "tot_evlu_amt"), number(summary, "nass_amt"), number(summary, "dnca_tot_amt"),
                number(summary, "prvs_rcdl_excc_amt"), purchase, number(summary, "evlu_amt_smtl_amt"),
                profitLoss, profitRate, holdings, Instant.now());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asString().strip();
    }

    private static BigDecimal number(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isEmpty()) return null;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return null; }
    }

    public record AccountBalance(String environment, String accountNumber, BigDecimal totalEvaluation, BigDecimal netAsset,
                                 BigDecimal deposit, BigDecimal settlementDeposit, BigDecimal purchaseAmount,
                                 BigDecimal evaluationAmount, BigDecimal profitLoss, BigDecimal profitRate,
                                 List<Holding> holdings, Instant fetchedAt) { }

    public record Holding(String code, String name, BigDecimal quantity, BigDecimal averagePrice, BigDecimal currentPrice,
                          BigDecimal purchaseAmount, BigDecimal evaluationAmount, BigDecimal profitLoss, BigDecimal profitRate) { }
}
