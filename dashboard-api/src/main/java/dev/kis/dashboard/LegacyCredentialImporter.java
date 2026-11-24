package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-off migration: copies {@code kis.openapi.<environment>.*} credentials from configuration into the database for
 * the user given by {@code kis.dashboard.import-credentials-for} (e.g. {@code github:123456}). Existing rows are kept.
 */
@Component
@ConditionalOnProperty("kis.dashboard.import-credentials-for")
public class LegacyCredentialImporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyCredentialImporter.class);
    private final KisProperties properties;
    private final KisUserAccounts accounts;
    private final String userKey;

    public LegacyCredentialImporter(KisProperties properties, KisUserAccounts accounts,
                                    @Value("${kis.dashboard.import-credentials-for}") String userKey) {
        this.properties = properties;
        this.accounts = accounts;
        this.userKey = userKey.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        for (KisEnvironment environment : KisEnvironment.values()) {
            KisProperties.Account configured = properties.account(environment);
            if (isBlank(configured.getAppKey()) || isBlank(configured.getAppSecret())) continue;
            if (accounts.find(userKey, environment).isPresent()) {
                log.info("KIS credentials for {} / {} already stored; skipped", userKey, environment);
                continue;
            }
            accounts.save(userKey, environment, new KisUserAccounts.CredentialInput(configured.getAppKey(),
                    configured.getAppSecret(), configured.getAccountNumber(), configured.getAccountProductCode()));
            log.info("Imported KIS credentials for {} / {} from configuration", userKey, environment);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
