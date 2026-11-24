package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisApiPage;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import dev.kis.openapi.auth.KisAccount;
import dev.kis.openapi.auth.KisCredentialProvider;
import dev.kis.openapi.internal.KisApiTransport;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.scheduler.Schedulers;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DomesticStockClient {
    private static final String PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String ORDER_PATH = "/uapi/domestic-stock/v1/trading/order-cash";
    private static final String BALANCE_PATH = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String AMEND_CANCEL_PATH = "/uapi/domestic-stock/v1/trading/order-rvsecncl";
    private static final String DAILY_ORDERS_PATH = "/uapi/domestic-stock/v1/trading/inquire-daily-ccld";
    private static final String PRICE_TR_ID = "FHKST01010100";
    static final int MAX_BALANCE_PAGES = 10;
    /** Spacing between continuation calls so paging stays under the KIS per-second limit. */
    private static final Duration PAGE_DELAY = Duration.ofMillis(100);

    private final KisApiTransport restClient;
    private final KisProperties properties;
    private final KisCredentialProvider credentialProvider;

    public DomesticStockClient(KisApiTransport restClient, KisProperties properties) {
        this(restClient, properties, new KisCredentialProvider(properties));
    }

    @Autowired
    public DomesticStockClient(KisApiTransport restClient, KisProperties properties, KisCredentialProvider credentialProvider) {
        this.restClient = restClient;
        this.properties = properties;
        this.credentialProvider = credentialProvider;
    }

    public Mono<KisApiResponse> getPrice(String symbol) {
        requireSymbol(symbol);
        return restClient.get(PRICE_PATH, PRICE_TR_ID, Map.of(
                "FID_COND_MRKT_DIV_CODE", "J",
                "FID_INPUT_ISCD", symbol));
    }

    public Mono<KisApiResponse> placeCashOrder(CashOrderRequest order) {
        KisEnvironment environment = properties.getEnvironment();
        String trId = orderTrId(environment, order.side());
        return account(environment).flatMap(account -> {
            String unitPrice = order.orderType() == CashOrderRequest.OrderType.MARKET
                    ? "0" : order.price().stripTrailingZeros().toPlainString();
            return restClient.post(ORDER_PATH, trId, Map.of(
                    "CANO", account.accountNumber(),
                    "ACNT_PRDT_CD", account.accountProductCode(),
                    "PDNO", order.symbol(),
                    "ORD_DVSN", order.orderType().kisCode(),
                    "ORD_QTY", Long.toString(order.quantity()),
                    "ORD_UNPR", unitPrice,
                    "EXCG_ID_DVSN_CD", "KRX",
                    "SLL_TYPE", order.side() == CashOrderRequest.Side.SELL ? "01" : "",
                    "CNDT_PRIC", ""));
        });
    }

    /**
     * Domestic stock balance across every page KIS returns. Holdings ({@code output1}) are concatenated in page
     * order and the account summary ({@code output2}) comes from the first page. Stops after
     * {@value #MAX_BALANCE_PAGES} pages, like the official samples.
     */
    public Mono<KisApiResponse> getBalance() {
        return getBalancePage("", "", "")
                .expand(page -> page.hasNextPage()
                        ? Mono.delay(PAGE_DELAY).then(getBalancePage(page.continuationSearchKey(), page.continuationKey(), "N"))
                        : Mono.empty())
                .take(MAX_BALANCE_PAGES)
                .collectList()
                .map(this::mergeBalancePages);
    }

    /** A single page of the domestic stock balance; pass empty keys and {@code tr_cont} for the first page. */
    public Mono<KisApiPage> getBalancePage(String continuationSearchKey, String continuationKey, String trCont) {
        KisEnvironment environment = properties.getEnvironment();
        return account(environment).flatMap(account -> {
            String trId = environment == KisEnvironment.REAL ? "TTTC8434R" : "VTTC8434R";
            return restClient.getPage(BALANCE_PATH, trId, Map.ofEntries(
                    Map.entry("CANO", account.accountNumber()),
                    Map.entry("ACNT_PRDT_CD", account.accountProductCode()),
                    Map.entry("AFHR_FLPR_YN", "N"),
                    Map.entry("OFL_YN", ""),
                    Map.entry("INQR_DVSN", "01"),
                    Map.entry("UNPR_DVSN", "01"),
                    Map.entry("FUND_STTL_ICLD_YN", "N"),
                    Map.entry("FNCG_AMT_AUTO_RDPT_YN", "N"),
                    Map.entry("PRCS_DVSN", "00"),
                    Map.entry("CTX_AREA_FK100", continuationSearchKey == null ? "" : continuationSearchKey),
                    Map.entry("CTX_AREA_NK100", continuationKey == null ? "" : continuationKey)),
                    trCont == null ? "" : trCont);
        });
    }

    private KisApiResponse mergeBalancePages(List<KisApiPage> pages) {
        KisApiPage last = pages.get(pages.size() - 1);
        if (pages.size() == MAX_BALANCE_PAGES && last.hasNextPage()) {
            log.warn("KIS balance has more than {} pages; returning the first {}", MAX_BALANCE_PAGES, MAX_BALANCE_PAGES);
        }
        if (pages.size() == 1) return pages.get(0).data();
        ArrayNode holdings = JsonNodeFactory.instance.arrayNode();
        for (KisApiPage page : pages) {
            JsonNode rows = page.data().output1();
            if (rows == null) continue;
            if (rows.isArray()) rows.forEach(holdings::add);
            else holdings.add(rows);
        }
        KisApiResponse first = pages.get(0).data();
        KisApiResponse lastData = last.data();
        return new KisApiResponse(lastData.rtCd(), lastData.msgCd(), lastData.msg1(), first.output(), holdings, first.output2());
    }

    public Mono<KisApiResponse> amendOrCancelOrder(OrderAmendCancelRequest request) {
        KisEnvironment environment = properties.getEnvironment();
        return account(environment).flatMap(account -> {
            String trId = environment == KisEnvironment.REAL ? "TTTC0013U" : "VTTC0013U";
            String unitPrice = request.unitPrice().stripTrailingZeros().toPlainString();
            return restClient.post(AMEND_CANCEL_PATH, trId, Map.ofEntries(
                    Map.entry("CANO", account.accountNumber()),
                    Map.entry("ACNT_PRDT_CD", account.accountProductCode()),
                    Map.entry("KRX_FWDG_ORD_ORGNO", request.orderBranchNo()),
                    Map.entry("ORGN_ODNO", request.originalOrderNo()),
                    Map.entry("ORD_DVSN", request.orderType().kisCode()),
                    Map.entry("RVSE_CNCL_DVSN_CD", request.action().code()),
                    Map.entry("ORD_QTY", Long.toString(request.quantity())),
                    Map.entry("ORD_UNPR", unitPrice),
                    Map.entry("QTY_ALL_ORD_YN", request.allRemaining() ? "Y" : "N"),
                    Map.entry("EXCG_ID_DVSN_CD", request.exchange().name()),
                    Map.entry("CNDT_PRIC", "")));
        });
    }

    public Mono<KisApiPage> getDailyOrders(DailyOrderQuery query) {
        KisEnvironment environment = properties.getEnvironment();
        return account(environment).flatMap(account -> {
            String trId = dailyOrdersTrId(environment, query.periodRange());
            Map<String, String> parameters = Map.ofEntries(
                    Map.entry("CANO", account.accountNumber()),
                    Map.entry("ACNT_PRDT_CD", account.accountProductCode()),
                    Map.entry("INQR_STRT_DT", query.kisStartDate()),
                    Map.entry("INQR_END_DT", query.kisEndDate()),
                    Map.entry("SLL_BUY_DVSN_CD", query.side().code()),
                    Map.entry("PDNO", query.symbol()),
                    Map.entry("CCLD_DVSN", query.fillStatus().code()),
                    Map.entry("INQR_DVSN", query.sortOrder().code()),
                    Map.entry("INQR_DVSN_3", query.productType().code()),
                    Map.entry("ORD_GNO_BRNO", query.orderBranchNo()),
                    Map.entry("ODNO", query.orderNo()),
                    Map.entry("INQR_DVSN_1", query.itemType()),
                    Map.entry("CTX_AREA_FK100", query.continuationSearchKey()),
                    Map.entry("CTX_AREA_NK100", query.continuationKey()),
                    Map.entry("EXCG_ID_DVSN_CD", query.exchangeCode()));
            return restClient.getPage(DAILY_ORDERS_PATH, trId, parameters, query.continuationHeader());
        });
    }

    private String orderTrId(KisEnvironment environment, CashOrderRequest.Side side) {
        if (environment == KisEnvironment.REAL) {
            return side == CashOrderRequest.Side.BUY ? "TTTC0012U" : "TTTC0011U";
        }
        return side == CashOrderRequest.Side.BUY ? "VTTC0012U" : "VTTC0011U";
    }

    private String dailyOrdersTrId(KisEnvironment environment, DailyOrderQuery.PeriodRange periodRange) {
        if (environment == KisEnvironment.REAL) {
            return periodRange == DailyOrderQuery.PeriodRange.WITHIN_THREE_MONTHS ? "TTTC0081R" : "CTSC9215R";
        }
        return periodRange == DailyOrderQuery.PeriodRange.WITHIN_THREE_MONTHS ? "VTTC0081R" : "VTSC9215R";
    }

    /** The account for this call (Reactor context first, then the default account), validated for trading APIs. */
    private Mono<KisAccount> account(KisEnvironment environment) {
        return Mono.deferContextual(context -> Mono.fromCallable(() -> {
                    KisAccount account = credentialProvider.resolveAccount(context, environment);
                    if (account.accountNumber().isEmpty()) {
                        throw new IllegalStateException("Set account-number for the selected kis.openapi environment");
                    }
                    return account;
                })
                .subscribeOn(Schedulers.boundedElastic()));
    }

    private void requireSymbol(String symbol) {
        if (symbol == null || !symbol.matches("\\d{6}")) {
            throw new IllegalArgumentException("symbol must be a six-digit KRX code");
        }
    }
}
