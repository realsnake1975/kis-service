package dev.kis.dashboard;

import dev.kis.openapi.domestic.DomesticStock;
import dev.kis.openapi.domestic.DomesticStockMasterClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomesticStockSearchServiceAdapterTest {
    @Test
    void sixDigitQueryUsesOnlyTheCodeFilter() {
        AtomicReference<String> name = new AtomicReference<>();
        AtomicReference<String> code = new AtomicReference<>();
        DomesticStockMasterClient client = new DomesticStockMasterClient() {
            @Override public Mono<List<DomesticStock>> search(String searchName, String searchCode) {
                name.set(searchName);
                code.set(searchCode);
                return Mono.just(List.of());
            }
        };

        new DomesticStockSearchServiceAdapter(client).search("005930").block();

        assertEquals(null, name.get());
        assertEquals("005930", code.get());
    }

    @Test
    void nameQueryUsesOnlyTheNameFilter() {
        AtomicReference<String> name = new AtomicReference<>();
        AtomicReference<String> code = new AtomicReference<>();
        DomesticStockMasterClient client = new DomesticStockMasterClient() {
            @Override public Mono<List<DomesticStock>> search(String searchName, String searchCode) {
                name.set(searchName);
                code.set(searchCode);
                return Mono.just(List.of());
            }
        };

        new DomesticStockSearchServiceAdapter(client).search("삼성전자").block();

        assertEquals("삼성전자", name.get());
        assertEquals(null, code.get());
    }
}
