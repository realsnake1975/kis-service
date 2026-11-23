package dev.kis.dashboard;

import dev.kis.openapi.KisProperties;
import dev.kis.openapi.domestic.DomesticStockPriceMonitor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MonitorPriceSchedule implements PriceSchedule {
    private final KisProperties properties;
    private final DomesticStockPriceMonitor monitor;
    private final DashboardWatchRepository watches;

    public MonitorPriceSchedule(KisProperties properties, DomesticStockPriceMonitor monitor, DashboardWatchRepository watches) {
        this.properties = properties;
        this.monitor = monitor;
        this.watches = watches;
    }

    @Override
    public synchronized void updateSymbols(List<String> codes) {
        properties.getDomesticStockMonitor().setSymbols(codes);
        monitor.stop();
        monitor.start();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restorePersistedSchedule() {
        updateSymbols(watches.findAll().stream().map(DashboardWatch::getCode).toList());
    }
}
