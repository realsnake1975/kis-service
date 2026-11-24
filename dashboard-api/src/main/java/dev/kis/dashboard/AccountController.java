package dev.kis.dashboard;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
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

    public AccountController(AccountBalanceSource source, KisProperties properties) {
        this.source = source;
        this.properties = properties;
    }

    @GetMapping("/api/account/balance")
    Mono<AccountBalance> balance() {
        return Mono.defer(source::balance)
                .onErrorMap(IllegalStateException.class, e -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "계좌 정보가 서버 설정에 없습니다."))
                .onErrorMap(e -> !(e instanceof ResponseStatusException), e -> {
                    log.warn("KIS balance inquiry failed", e);
                    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "한국투자증권 잔고 조회에 실패했습니다.");
                })
                .map(this::toBalance);
    }

    AccountBalance toBalance(KisApiResponse response) {
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
        var environment = properties.getEnvironment();
        return new AccountBalance(environment.name(),
                DashboardController.maskAccount(properties.account(environment).getAccountNumber()),
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
