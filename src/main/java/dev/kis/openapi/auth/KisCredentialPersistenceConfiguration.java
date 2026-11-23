package dev.kis.openapi.auth;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration(afterName = "org.springframework.boot.jpa.autoconfigure.JpaRepositoriesAutoConfiguration")
@ConditionalOnClass({KisCredentialRepository.class, HikariDataSource.class})
@ConditionalOnBean(HikariDataSource.class)
@EntityScan(basePackageClasses = KisApiCredential.class)
@EnableJpaRepositories(basePackageClasses = KisCredentialRepository.class)
public class KisCredentialPersistenceConfiguration {
}
