package dev.kis.dashboard;

import dev.kis.openapi.domestic.DomesticStock;
import reactor.core.publisher.Mono;

import java.util.List;

public interface DomesticStockSearchService {
    Mono<List<DomesticStock>> search(String query);
}
