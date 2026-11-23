package dev.kis.openapi;

import dev.kis.openapi.domestic.DomesticStockPriceEntity;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * When quote persistence is enabled, adds the library's JPA package to the application's auto-configuration
 * packages, so that Spring Boot's Hibernate and Spring Data JPA auto-configurations pick up the library's
 * entity and repository alongside the application's own.
 *
 * <p>This must run before Hibernate's auto-configuration reads the packages to scan. It adds to,
 * rather than replaces, the consuming application's own packages, which {@code @EntityScan} would do.
 * Applications that declare their own {@code @EntityScan} or {@code @EnableJpaRepositories} must include
 * this package themselves.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
@ConditionalOnProperty(prefix = "kis.openapi.domestic-stock-monitor.persistence", name = "enabled", havingValue = "true")
@Import(KisJpaEntityPackagesConfiguration.Registrar.class)
public class KisJpaEntityPackagesConfiguration {

    static class Registrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            AutoConfigurationPackages.register(registry, DomesticStockPriceEntity.class.getPackageName());
        }
    }
}
