package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomesticStockPricePersistenceListenerTest {
    @Test
    void looksUpMasterNameAndPersistsSuccessfulEventAsynchronously() throws Exception {
        AtomicReference<DomesticStockPriceEntity> saved = new AtomicReference<>();
        CountDownLatch savedLatch = new CountDownLatch(1);
        DomesticStockPriceRepository repository = recordingRepository(saved, savedLatch);
        StubMasterClient master = new StubMasterClient(List.of(new DomesticStock(
                "005930", "KR7005930003", "삼성전자", DomesticStock.Market.KOSPI)));
        DomesticStockPricePersistenceListener listener = new DomesticStockPricePersistenceListener(repository, master);

        listener.onPrice(successEvent());

        assertTrue(savedLatch.await(3, TimeUnit.SECONDS));
        assertEquals("삼성전자", saved.get().getStockName());
        assertEquals("005930", saved.get().getStockCode());
        assertEquals(1, master.lookups.get());
    }

    @Test
    void doesNotPersistFailedKisEvent() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        DomesticStockPriceRepository repository = recordingRepository(new AtomicReference<>(), new CountDownLatch(1), saves);
        StubMasterClient master = new StubMasterClient(List.of());
        DomesticStockPricePersistenceListener listener = new DomesticStockPricePersistenceListener(repository, master);
        DomesticStockPriceEvent failure = new DomesticStockPriceEvent("005930", Instant.now(),
                new KisApiResponse("1", "ERROR", "rate limited", null, null, null), "rate limited");

        listener.onPrice(failure);

        assertEquals(0, saves.get());
        assertEquals(0, master.lookups.get());
    }

    private DomesticStockPriceEvent successEvent() throws Exception {
        var output = new JsonMapper().readTree("{\"stck_prpr\":\"70000\"}");
        return new DomesticStockPriceEvent("005930", Instant.now(),
                new KisApiResponse("0", "0", "ok", output, null, null), null);
    }

    private static final class StubMasterClient extends DomesticStockMasterClient {
        private final List<DomesticStock> stocks;
        private final AtomicInteger lookups = new AtomicInteger();

        private StubMasterClient(List<DomesticStock> stocks) {
            this.stocks = stocks;
        }

        @Override
        public Mono<List<DomesticStock>> search(String name, String code) {
            lookups.incrementAndGet();
            return Mono.just(stocks);
        }
    }

    private DomesticStockPriceRepository recordingRepository(
            AtomicReference<DomesticStockPriceEntity> saved, CountDownLatch savedLatch) {
        return recordingRepository(saved, savedLatch, new AtomicInteger());
    }

    private DomesticStockPriceRepository recordingRepository(
            AtomicReference<DomesticStockPriceEntity> saved, CountDownLatch savedLatch, AtomicInteger saves) {
        return (DomesticStockPriceRepository) Proxy.newProxyInstance(
                DomesticStockPriceRepository.class.getClassLoader(),
                new Class<?>[]{DomesticStockPriceRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("save")) {
                        saves.incrementAndGet();
                        saved.set((DomesticStockPriceEntity) args[0]);
                        savedLatch.countDown();
                        return args[0];
                    }
                    if (method.getName().equals("toString")) return "RecordingDomesticStockPriceRepository";
                    return null;
                });
    }
}
