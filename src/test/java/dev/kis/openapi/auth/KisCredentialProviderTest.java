package dev.kis.openapi.auth;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KisCredentialProviderTest {
    @Test
    void readsCredentialsForTheSelectedEnvironmentFromProperties() {
        KisProperties properties = new KisProperties();
        properties.getPaper().setAppKey("paper-key");
        properties.getPaper().setAppSecret("paper-secret");
        properties.getReal().setAppKey(" real-key ");
        properties.getReal().setAppSecret("real-secret\n");
        KisCredentialProvider provider = new KisCredentialProvider(properties);

        assertEquals(new KisApiCredentials("paper-key", "paper-secret"), provider.getCredentials(KisEnvironment.PAPER));
        assertEquals(new KisApiCredentials("real-key", "real-secret"), provider.getCredentials(KisEnvironment.REAL));
    }

    @Test
    void failsClearlyWhenEnvironmentCredentialsAreMissing() {
        KisProperties properties = new KisProperties();
        properties.getReal().setAppKey("real-key");
        properties.getReal().setAppSecret("real-secret");
        KisCredentialProvider provider = new KisCredentialProvider(properties);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider.getCredentials(KisEnvironment.PAPER));

        assertEquals("KIS app credentials are not configured for environment PAPER "
                + "(kis.openapi.paper.app-key / app-secret)", error.getMessage());
        assertFalse(provider.isConfigured(KisEnvironment.PAPER));
        assertTrue(provider.isConfigured(KisEnvironment.REAL));
    }

    @Test
    void doesNotExposeSecretsInToString() {
        assertEquals("KisApiCredentials[appKey=****, appSecret=****]",
                new KisApiCredentials("key", "secret").toString());
    }
}
