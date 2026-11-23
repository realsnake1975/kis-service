package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisProperties;
import dev.kis.openapi.KisApiPage;
import dev.kis.openapi.internal.KisApiTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
class DomesticStockPriceMonitorTest {
    private final RecordingStockClient stockClient = new RecordingStockClient();
    private final KisProperties kisProperties = new KisProperties();
    private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
    private final ApplicationEventPublisher eventPublisher = events::add;
    private DomesticStockPriceMonitor monitor;

    @AfterEach
    void stopMonitor() {
        if (monitor != null) monitor.stop();
    }

    @Test
    void doesNotPollWhenDisabled() {
        kisProperties.getDomesticStockMonitor().setSymbols(List.of("005930"));
        monitor = new DomesticStockPriceMonitor(stockClient, kisProperties, eventPublisher);

        monitor.start();

        assertFalse(monitor.isRunning());
        assertTrue(stockClient.calls.isEmpty());
    }

    @Test
    void pollsConfiguredSymbolsAndPublishesEachSuccessfulResponse() throws Exception {
        KisProperties.DomesticStockMonitor config = kisProperties.getDomesticStockMonitor();
        config.setEnabled(true);
        config.setInterval(Duration.ofSeconds(10));
        config.setSymbols(List.of("005930", "000660"));
        stockClient.responseFactory = symbol -> Mono.just(success());
        monitor = new DomesticStockPriceMonitor(stockClient, kisProperties, eventPublisher);

        monitor.start();

        DomesticStockPriceEvent first = nextEvent();
        DomesticStockPriceEvent second = nextEvent();
        assertEquals(List.of("005930", "000660"), List.of(first.symbol(), second.symbol()));
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertNotNull(first.observedAt());
        assertEquals(List.of("005930", "000660"), stockClient.calls);
    }

    @Test
    void publishesLookupFailuresWithoutStoppingTheMonitor() throws Exception {
        KisProperties.DomesticStockMonitor config = kisProperties.getDomesticStockMonitor();
        config.setEnabled(true);
        config.setInterval(Duration.ofSeconds(10));
        config.setSymbols(List.of("005930"));
        stockClient.responseFactory = symbol -> Mono.error(new IllegalStateException("network down"));
        monitor = new DomesticStockPriceMonitor(stockClient, kisProperties, eventPublisher);

        monitor.start();

        DomesticStockPriceEvent event = nextEvent();
        assertFalse(event.isSuccess());
        assertEquals("network down", event.errorMessage());
        assertEquals("005930", event.symbol());
    }

    @Test
    void reportsKisBusinessFailureInPublishedEvent() throws Exception {
        KisProperties.DomesticStockMonitor config = kisProperties.getDomesticStockMonitor();
        config.setEnabled(true);
        config.setInterval(Duration.ofSeconds(10));
        config.setSymbols(List.of("005930"));
        stockClient.responseFactory = symbol -> Mono.just(
                new KisApiResponse("1", "EGW00123", "rate limited", null, null, null));
        monitor = new DomesticStockPriceMonitor(stockClient, kisProperties, eventPublisher);

        monitor.start();

        DomesticStockPriceEvent event = nextEvent();
        assertFalse(event.isSuccess());
        assertEquals("rate limited", event.errorMessage());
        assertEquals("EGW00123", event.response().msgCd());
    }

    @Test
    void rejectsInvalidConfiguredStockCode() {
        KisProperties.DomesticStockMonitor config = kisProperties.getDomesticStockMonitor();
        config.setEnabled(true);
        config.setSymbols(List.of("5930"));
        monitor = new DomesticStockPriceMonitor(stockClient, kisProperties, eventPublisher);

        assertThrows(IllegalStateException.class, monitor::start);
        assertTrue(stockClient.calls.isEmpty());
    }

    @Test
    void skipsTicksWhilePreviousPollingCycleIsStillRunning() {
        KisProperties.DomesticStockMonitor config = kisProperties.getDomesticStockMonitor();
        config.setEnabled(true);
        config.setInterval(Duration.ofMillis(20));
        config.setSymbols(List.of("005930"));
        stockClient.responseFactory = symbol -> Mono.delay(Duration.ofMillis(700)).map(ignored -> success());
        monitor = new DomesticStockPriceMonitor(stockClient, kisProperties, eventPublisher);

        monitor.start();

        try {
            Thread.sleep(350);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted while checking overlapping ticks", interrupted);
        }
        assertEquals(1, stockClient.calls.size());
        assertTrue(monitor.isRunning());
    }

    private DomesticStockPriceEvent nextEvent() throws InterruptedException {
        Object event = events.poll(3, TimeUnit.SECONDS);
        assertNotNull(event, "Expected a published price event");
        assertTrue(event instanceof DomesticStockPriceEvent);
        return (DomesticStockPriceEvent) event;
    }

    private KisApiResponse success() {
        return new KisApiResponse("0", "0", "ok", null, null, null);
    }

    private static final class RecordingStockClient extends DomesticStockClient {
        private final List<String> calls = new CopyOnWriteArrayList<>();
        private volatile Function<String, Mono<KisApiResponse>> responseFactory = symbol -> Mono.just(
                new KisApiResponse("0", "0", "ok", null, null, null));

        private RecordingStockClient() {
            super(new NoopTransport(), new KisProperties());
        }

        @Override
        public Mono<KisApiResponse> getPrice(String symbol) {
            calls.add(symbol);
            return responseFactory.apply(symbol);
        }
    }

    private static final class NoopTransport implements KisApiTransport {
        @Override
        public Mono<KisApiResponse> get(String path, String trId, Map<String, String> query) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<KisApiPage> getPage(String path, String trId, Map<String, String> query, String trCont) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<KisApiResponse> post(String path, String trId, Map<String, ?> body) {
            return Mono.error(new UnsupportedOperationException());
        }
    }
}
