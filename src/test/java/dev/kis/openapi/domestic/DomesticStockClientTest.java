package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiPage;
import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import dev.kis.openapi.internal.KisApiTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomesticStockClientTest {
    private RecordingTransport transport;
    private KisProperties properties;
    private DomesticStockClient client;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport();
        properties = new KisProperties();
        properties.setEnvironment(KisEnvironment.PAPER);
        properties.getPaper().setAccountNumber("12345678");
        properties.getPaper().setAccountProductCode("01");
        client = new DomesticStockClient(transport, properties);
    }

    @Test
    void usesPaperTransactionIdAndPayloadForAmendment() {
        OrderAmendCancelRequest request = new OrderAmendCancelRequest("00123", "0000123456",
                CashOrderRequest.OrderType.LIMIT, OrderAmendCancelRequest.Action.REVISE,
                1, new BigDecimal("71000"), false, OrderAmendCancelRequest.Exchange.KRX);

        client.amendOrCancelOrder(request).block();

        assertEquals("POST", transport.method);
        assertEquals("/uapi/domestic-stock/v1/trading/order-rvsecncl", transport.path);
        assertEquals("VTTC0013U", transport.trId);
        assertEquals("12345678", transport.body.get("CANO"));
        assertEquals("0000123456", transport.body.get("ORGN_ODNO"));
        assertEquals("01", transport.body.get("RVSE_CNCL_DVSN_CD"));
        assertEquals("71000", transport.body.get("ORD_UNPR"));
    }

    @Test
    void selectsMockTransactionIdAndParametersForDailyOrders() {
        DailyOrderQuery query = DailyOrderQuery.all(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5));

        client.getDailyOrders(query).block();

        assertEquals("GET_PAGE", transport.method);
        assertEquals("/uapi/domestic-stock/v1/trading/inquire-daily-ccld", transport.path);
        assertEquals("VTTC0081R", transport.trId);
        assertEquals("12345678", transport.query.get("CANO"));
        assertEquals("20260102", transport.query.get("INQR_STRT_DT"));
        assertEquals("00", transport.query.get("CCLD_DVSN"));
        assertEquals("", transport.trCont);
    }

    @Test
    void selectsRealTransactionIdForOlderHistory() {
        properties.setEnvironment(KisEnvironment.REAL);
        properties.getReal().setAccountNumber("87654321");
        properties.getReal().setAccountProductCode("01");
        DailyOrderQuery query = new DailyOrderQuery(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1),
                DailyOrderQuery.PeriodRange.BEFORE_THREE_MONTHS, DailyOrderQuery.Side.ALL,
                DailyOrderQuery.FillStatus.ALL, DailyOrderQuery.SortOrder.NEWEST_FIRST,
                DailyOrderQuery.ProductType.ALL, "", "", "", "", "KRX", "", "", "");

        client.getDailyOrders(query).block();

        assertEquals("CTSC9215R", transport.trId);
        assertEquals("87654321", transport.query.get("CANO"));
    }

    private static final class RecordingTransport implements KisApiTransport {
        private String method;
        private String path;
        private String trId;
        private String trCont;
        private Map<String, String> query;
        private Map<String, ?> body;

        @Override
        public Mono<KisApiResponse> get(String path, String trId, Map<String, String> query) {
            record("GET", path, trId);
            this.query = query;
            return Mono.just(success());
        }

        @Override
        public Mono<KisApiPage> getPage(String path, String trId, Map<String, String> query, String trCont) {
            record("GET_PAGE", path, trId);
            this.query = query;
            this.trCont = trCont;
            return Mono.just(new KisApiPage(success(), "D"));
        }

        @Override
        public Mono<KisApiResponse> post(String path, String trId, Map<String, ?> body) {
            record("POST", path, trId);
            this.body = body;
            return Mono.just(success());
        }

        private void record(String method, String path, String trId) {
            this.method = method;
            this.path = path;
            this.trId = trId;
        }

        private KisApiResponse success() {
            return new KisApiResponse("0", "0", "ok", null, null, null);
        }
    }
}
