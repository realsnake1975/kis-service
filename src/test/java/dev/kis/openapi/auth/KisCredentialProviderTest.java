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

    @Test
    void contextAccountWinsOverSourcesAndSourcesWinOverProperties() {
        KisProperties properties = new KisProperties();
        properties.getReal().setAppKey("cfg-key");
        properties.getReal().setAppSecret("cfg-secret");
        KisAccount owner = new KisAccount("owner-key", "owner-secret", "11112222", "01");
        KisAccount user = new KisAccount("user-key", "user-secret", "33334444", "01");
        KisAccountSource emptySource = environment -> java.util.Optional.empty();
        KisAccountSource ownerSource = environment -> java.util.Optional.of(owner);

        KisCredentialProvider withSource = new KisCredentialProvider(properties, java.util.List.of(emptySource, ownerSource));
        KisCredentialProvider withoutSource = new KisCredentialProvider(properties);

        assertEquals(user, withSource.resolve(KisAccountContext.with(user), KisEnvironment.REAL));
        assertEquals(owner, withSource.resolve(reactor.util.context.Context.empty(), KisEnvironment.REAL));
        assertEquals("cfg-key", withoutSource.resolve(reactor.util.context.Context.empty(), KisEnvironment.REAL).appKey());
    }

    @Test
    void sourceWithoutCredentialsFallsBackToProperties() {
        KisProperties properties = new KisProperties();
        KisCredentialProvider provider = new KisCredentialProvider(properties,
                java.util.List.of(environment -> java.util.Optional.of(new KisAccount("", "", "", null))));
        assertFalse(provider.isConfigured(KisEnvironment.REAL));
    }

    @Test
    void accountDoesNotExposeSecretsInToString() {
        assertEquals("KisAccount[appKey=****, appSecret=****, accountNumber=****, accountProductCode=01]",
                new KisAccount("key", "secret", "12345678", null).toString());
    }
}
