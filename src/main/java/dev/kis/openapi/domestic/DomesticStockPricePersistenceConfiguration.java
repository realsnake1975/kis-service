package dev.kis.openapi.domestic;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration(afterName = "org.springframework.boot.jpa.autoconfigure.JpaRepositoriesAutoConfiguration")
@ConditionalOnClass({DomesticStockPriceRepository.class, HikariDataSource.class})
@ConditionalOnBean(HikariDataSource.class)
@ConditionalOnProperty(prefix = "kis.openapi.domestic-stock-monitor.persistence", name = "enabled", havingValue = "true")
@EntityScan(basePackageClasses = DomesticStockPriceEntity.class)
@EnableJpaRepositories(basePackageClasses = DomesticStockPriceRepository.class)
public class DomesticStockPricePersistenceConfiguration {
    @Bean
    DomesticStockPricePersistenceListener domesticStockPricePersistenceListener(
            DomesticStockPriceRepository repository, DomesticStockMasterClient masterClient) {
        return new DomesticStockPricePersistenceListener(repository, masterClient);
    }
}
