package dev.kis.openapi.auth;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Supplies the KIS app key and secret for an environment from {@code kis.openapi.paper.*} or
 * {@code kis.openapi.real.*}. There is no fallback between environments.
 */
@Component
@RequiredArgsConstructor
public class KisCredentialProvider {
    private final KisProperties properties;

    public KisApiCredentials getCredentials(KisEnvironment environment) {
        KisProperties.Account account = properties.account(environment);
        if (isBlank(account.getAppKey()) || isBlank(account.getAppSecret())) {
            throw new IllegalStateException("KIS app credentials are not configured for environment " + environment
                    + " (kis.openapi." + environment.name().toLowerCase() + ".app-key / app-secret)");
        }
        return new KisApiCredentials(account.getAppKey().strip(), account.getAppSecret().strip());
    }

    public boolean isConfigured(KisEnvironment environment) {
        KisProperties.Account account = properties.account(environment);
        return !isBlank(account.getAppKey()) && !isBlank(account.getAppSecret());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
