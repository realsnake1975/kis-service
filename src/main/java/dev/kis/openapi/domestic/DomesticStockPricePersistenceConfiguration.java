package dev.kis.openapi.domestic;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = {"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
@ConditionalOnClass({DomesticStockPriceRepository.class, HikariDataSource.class})
@ConditionalOnBean(HikariDataSource.class)
@ConditionalOnProperty(prefix = "kis.openapi.domestic-stock-monitor.persistence", name = "enabled", havingValue = "true")
public class DomesticStockPricePersistenceConfiguration {
    @Bean
    DomesticStockPricePersistenceListener domesticStockPricePersistenceListener(
            DomesticStockPriceRepository repository, DomesticStockMasterClient masterClient) {
        return new DomesticStockPricePersistenceListener(repository, masterClient);
    }
}
