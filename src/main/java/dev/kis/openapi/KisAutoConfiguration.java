package dev.kis.openapi;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.kis.openapi.auth.KisTokenProvider;
import dev.kis.openapi.domestic.DomesticStockClient;
import dev.kis.openapi.domestic.DomesticStockMasterClient;
import dev.kis.openapi.domestic.DomesticStockPriceMonitor;
import dev.kis.openapi.internal.KisRestClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Duration;

@AutoConfiguration
@EnableCaching
@EnableConfigurationProperties(KisProperties.class)
@Import({KisTokenProvider.class, KisRestClient.class, DomesticStockClient.class, DomesticStockMasterClient.class,
        DomesticStockPriceMonitor.class})
public class KisAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    CacheManager kisCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("kisAccessTokens");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(Duration.ofHours(23)));
        return manager;
    }
}
