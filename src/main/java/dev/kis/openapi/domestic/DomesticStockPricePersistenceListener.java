package dev.kis.openapi.domestic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/** Resolves the display name and saves quote events without blocking the polling thread. */
@Slf4j
@RequiredArgsConstructor
public class DomesticStockPricePersistenceListener {
    private final DomesticStockPriceRepository repository;
    private final DomesticStockMasterClient masterClient;

    @EventListener
    public void onPrice(DomesticStockPriceEvent event) {
        if (!event.isSuccess() || event.response().output() == null) return;

        masterClient.search(null, event.symbol())
                .onErrorResume(error -> {
                    log.warn("Could not resolve the name for stock {}: {}", event.symbol(), error.getMessage());
                    return Mono.just(List.of());
                })
                .map(matches -> matches.isEmpty() ? "" : matches.get(0).name())
                .flatMap(name -> Mono.fromRunnable(() -> repository.save(DomesticStockPriceEntity.from(event, name)))
                        .subscribeOn(Schedulers.boundedElastic()))
                .doOnError(error -> log.error("Could not persist the price for stock {}", event.symbol(), error))
                .subscribe();
    }
}
