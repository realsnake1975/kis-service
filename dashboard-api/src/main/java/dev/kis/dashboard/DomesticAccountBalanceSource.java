package dev.kis.dashboard;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.domestic.DomesticStockClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DomesticAccountBalanceSource implements AccountBalanceSource {
    private final DomesticStockClient client;

    public DomesticAccountBalanceSource(DomesticStockClient client) {
        this.client = client;
    }

    @Override
    public Mono<KisApiResponse> balance() {
        return Mono.defer(client::getBalance);
    }
}
