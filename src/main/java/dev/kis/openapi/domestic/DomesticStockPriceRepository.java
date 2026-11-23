package dev.kis.openapi.domestic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DomesticStockPriceRepository extends JpaRepository<DomesticStockPriceEntity, Long> {
    List<DomesticStockPriceEntity> findByStockCodeAndObservedAtBetweenOrderByObservedAtAsc(
            String stockCode, Instant from, Instant to);
    Optional<DomesticStockPriceEntity> findFirstByStockCodeOrderByObservedAtDesc(String stockCode);
}
