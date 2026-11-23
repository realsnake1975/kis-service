package dev.kis.dashboard;

import dev.kis.openapi.domestic.DomesticStock;
import dev.kis.openapi.domestic.DomesticStockMasterClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class DomesticStockSearchServiceAdapter implements DomesticStockSearchService {
    private final DomesticStockMasterClient stockMaster;

    public DomesticStockSearchServiceAdapter(DomesticStockMasterClient stockMaster) {
        this.stockMaster = stockMaster;
    }

    @Override
    public Mono<List<DomesticStock>> search(String query) {
        String code = query != null && query.matches("\\d{6}") ? query : null;
        return stockMaster.search(code == null ? query : null, code);
    }
}
