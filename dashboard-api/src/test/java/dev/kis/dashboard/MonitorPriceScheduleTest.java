package dev.kis.dashboard;

import dev.kis.openapi.KisProperties;
import dev.kis.openapi.domestic.DomesticStockPriceMonitor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonitorPriceScheduleTest {
    @Test
    void updatesConfiguredSymbolsAndRestartsMonitor() {
        KisProperties properties = new KisProperties();
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        DomesticStockPriceMonitor monitor = new DomesticStockPriceMonitor(null, properties, null) {
            @Override public synchronized void start() { starts.incrementAndGet(); }
            @Override public synchronized void stop() { stops.incrementAndGet(); }
        };

        new MonitorPriceSchedule(properties, monitor, null).updateSymbols(List.of("005930", "000660"));

        assertEquals(List.of("005930", "000660"), properties.getDomesticStockMonitor().getSymbols());
        assertEquals(1, stops.get());
        assertEquals(1, starts.get());
    }
}
