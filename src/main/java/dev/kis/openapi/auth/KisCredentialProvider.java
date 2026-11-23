package dev.kis.openapi.auth;

import dev.kis.openapi.KisEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KisCredentialProvider {
    private final KisCredentialRepository repository;

    public KisApiCredentials getCredentials(KisEnvironment environment) {
        KisApiCredential credential = repository.findById(environment)
                .orElseThrow(() -> new IllegalStateException(
                        "No KIS app credentials are stored for environment " + environment));
        if (credential.getAppKey() == null || credential.getAppKey().isBlank()
                || credential.getAppSecret() == null || credential.getAppSecret().isBlank()) {
            throw new IllegalStateException("KIS app credentials are empty for environment " + environment);
        }
        return new KisApiCredentials(credential.getAppKey(), credential.getAppSecret());
    }
}
