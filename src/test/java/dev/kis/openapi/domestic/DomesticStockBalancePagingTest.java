package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiPage;
import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import dev.kis.openapi.internal.KisApiTransport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomesticStockBalancePagingTest {
    private final JsonMapper json = JsonMapper.builder().build();

    private KisProperties properties() {
        KisProperties properties = new KisProperties();
        properties.setEnvironment(KisEnvironment.REAL);
        properties.getReal().setAccountNumber("12345678");
        properties.getReal().setAccountProductCode("01");
        return properties;
    }

    private KisApiPage page(String holdings, String summary, String fk, String nk, String trCont) {
        String body = "{\"rt_cd\":\"0\",\"msg_cd\":\"KIOK0510\",\"msg1\":\"ok\",\"output1\":" + holdings + ",\"output2\":" + summary
                + ",\"ctx_area_fk100\":\"" + fk + "\",\"ctx_area_nk100\":\"" + nk + "\"}";
        return new KisApiPage(json.readValue(body, KisApiResponse.class), trCont);
    }

    @Test
    void followsContinuationPagesAndMergesHoldings() {
        var transport = new PagedTransport(i -> switch (i) {
            case 0 -> page("[{\"pdno\":\"005930\"},{\"pdno\":\"000660\"}]", "[{\"tot_evlu_amt\":\"1000\"}]", "FK1", "NK1", "M");
            case 1 -> page("[{\"pdno\":\"035420\"}]", "[{\"tot_evlu_amt\":\"9999\"}]", "FK2", "NK2", "M");
            default -> page("[{\"pdno\":\"035720\"}]", "[{\"tot_evlu_amt\":\"9999\"}]", "", "", "D");
        });
        var client = new DomesticStockClient(transport, properties());

        KisApiResponse balance = client.getBalance().block();

        assertEquals(3, transport.calls.size());
        assertEquals("", transport.calls.get(0).trCont());
        assertEquals("", transport.calls.get(0).query().get("CTX_AREA_NK100"));
        assertEquals("N", transport.calls.get(1).trCont());
        assertEquals("FK1", transport.calls.get(1).query().get("CTX_AREA_FK100"));
        assertEquals("NK1", transport.calls.get(1).query().get("CTX_AREA_NK100"));
        assertEquals("NK2", transport.calls.get(2).query().get("CTX_AREA_NK100"));
        assertEquals("TTTC8434R", transport.calls.get(0).trId());
        assertEquals(4, balance.output1().size());
        assertEquals("035720", balance.output1().get(3).path("pdno").asString());
        assertEquals("1000", balance.output2().get(0).path("tot_evlu_amt").asString());
    }

    @Test
    void singlePageIsReturnedAsIs() {
        var transport = new PagedTransport(i -> page("[{\"pdno\":\"005930\"}]", "[{}]", "", "", "D"));
        KisApiResponse balance = new DomesticStockClient(transport, properties()).getBalance().block();
        assertEquals(1, transport.calls.size());
        assertEquals(1, balance.output1().size());
    }

    @Test
    void stopsWhenKisSaysMoreButSendsNoKeys() {
        var transport = new PagedTransport(i -> page("[{\"pdno\":\"005930\"}]", "[{}]", "", "", "M"));
        new DomesticStockClient(transport, properties()).getBalance().block();
        assertEquals(1, transport.calls.size());
    }

    @Test
    void stopsAfterTheMaximumNumberOfPages() {
        var transport = new PagedTransport(i -> page("[{\"pdno\":\"" + String.format("%06d", i) + "\"}]", "[{}]", "FK", "NK" + i, "M"));
        KisApiResponse balance = new DomesticStockClient(transport, properties()).getBalance().block();
        assertEquals(DomesticStockClient.MAX_BALANCE_PAGES, transport.calls.size());
        assertEquals(DomesticStockClient.MAX_BALANCE_PAGES, balance.output1().size());
    }

    @Test
    void usesTheContextAccountNumberForEveryPage() {
        var transport = new PagedTransport(i -> i == 0
                ? page("[{\"pdno\":\"005930\"}]", "[{}]", "FK", "NK", "M")
                : page("[{\"pdno\":\"000660\"}]", "[{}]", "", "", "D"));
        var account = new dev.kis.openapi.auth.KisAccount("k", "s", "99998888", "22");

        new DomesticStockClient(transport, properties()).getBalance()
                .contextWrite(dev.kis.openapi.auth.KisAccountContext.with(account)).block();

        assertEquals(2, transport.calls.size());
        transport.calls.forEach(call -> {
            assertEquals("99998888", call.query().get("CANO"));
            assertEquals("22", call.query().get("ACNT_PRDT_CD"));
        });
    }

    private record Call(String trId, Map<String, String> query, String trCont) { }

    private static final class PagedTransport implements KisApiTransport {
        private final IntFunction<KisApiPage> pages;
        private final List<Call> calls = new ArrayList<>();

        PagedTransport(IntFunction<KisApiPage> pages) { this.pages = pages; }

        @Override
        public Mono<KisApiPage> getPage(String path, String trId, Map<String, String> query, String trCont) {
            return Mono.fromSupplier(() -> {
                calls.add(new Call(trId, query, trCont));
                return pages.apply(calls.size() - 1);
            });
        }

        @Override
        public Mono<KisApiResponse> get(String path, String trId, Map<String, String> query) { throw new UnsupportedOperationException(); }

        @Override
        public Mono<KisApiResponse> post(String path, String trId, Map<String, ?> body) { throw new UnsupportedOperationException(); }
    }
}
