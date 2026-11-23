package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

/** Polls configured domestic stock codes sequentially and publishes each result as an application event. */
@Component
@RequiredArgsConstructor
public class DomesticStockPriceMonitor implements SmartLifecycle {
    private static final Pattern STOCK_CODE = Pattern.compile("\\d{6}");
    private static final Duration REQUEST_SPACING = Duration.ofMillis(100);

    private final DomesticStockClient stockClient;
    private final KisProperties kisProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile Disposable subscription;
    private volatile Disposable activePoll;

    @Override
    public synchronized void start() {
        if (subscription != null && !subscription.isDisposed()) return;
        if (!kisProperties.getDomesticStockMonitor().isEnabled()
                || kisProperties.getDomesticStockMonitor().getSymbols().isEmpty()) return;
        validateConfiguration();
        Duration interval = kisProperties.getDomesticStockMonitor().getInterval();
        subscription = Flux.interval(Duration.ZERO, interval)
                .subscribe(tick -> {
                    if (!polling.compareAndSet(false, true)) return;
                    activePoll = pollOnce()
                            .doFinally(signal -> polling.set(false))
                            .subscribe();
                });
    }

    private Mono<Void> pollOnce() {
        List<String> symbols = List.copyOf(kisProperties.getDomesticStockMonitor().getSymbols());
        return Flux.fromIterable(symbols)
                .concatMap(symbol -> Mono.delay(REQUEST_SPACING)
                        .then(stockClient.getPrice(symbol)
                                .doOnNext(response -> publish(symbol, response,
                                        response.isSuccess() ? null : response.msg1()))
                                .onErrorResume(error -> {
                                    publish(symbol, null, errorMessage(error));
                                    return Mono.empty();
                                })))
                .then();
    }

    private void publish(String symbol, KisApiResponse response, String errorMessage) {
        eventPublisher.publishEvent(new DomesticStockPriceEvent(symbol, Instant.now(), response, errorMessage));
    }

    private void validateConfiguration() {
        Duration interval = kisProperties.getDomesticStockMonitor().getInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalStateException("kis.openapi.domestic-stock-monitor.interval must be positive");
        }
        for (String symbol : kisProperties.getDomesticStockMonitor().getSymbols()) {
            if (symbol == null || !STOCK_CODE.matcher(symbol.trim()).matches()) {
                throw new IllegalStateException("kis.openapi.domestic-stock-monitor.symbols must contain six-digit codes");
            }
        }
    }

    private String errorMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    @Override
    public synchronized void stop() {
        if (activePoll != null) {
            activePoll.dispose();
            activePoll = null;
            polling.set(false);
        }
        if (subscription != null) {
            subscription.dispose();
            subscription = null;
        }
    }

    @Override
    public boolean isRunning() {
        return subscription != null && !subscription.isDisposed();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
