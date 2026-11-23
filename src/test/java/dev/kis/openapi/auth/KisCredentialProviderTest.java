package dev.kis.openapi.auth;

import dev.kis.openapi.KisEnvironment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KisCredentialProviderTest {
    @Test
    void readsCredentialsForTheSelectedEnvironmentFromRepository() {
        KisCredentialProvider provider = new KisCredentialProvider(repository(Map.of(
                KisEnvironment.PAPER, KisApiCredential.create(KisEnvironment.PAPER, "paper-key", "paper-secret"),
                KisEnvironment.REAL, KisApiCredential.create(KisEnvironment.REAL, "real-key", "real-secret"))));

        assertEquals(new KisApiCredentials("paper-key", "paper-secret"), provider.getCredentials(KisEnvironment.PAPER));
        assertEquals(new KisApiCredentials("real-key", "real-secret"), provider.getCredentials(KisEnvironment.REAL));
    }

    @Test
    void failsClearlyWhenEnvironmentCredentialsAreMissing() {
        KisCredentialProvider provider = new KisCredentialProvider(repository(Map.of()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider.getCredentials(KisEnvironment.PAPER));

        assertEquals("No KIS app credentials are stored for environment PAPER", error.getMessage());
    }

    private KisCredentialRepository repository(Map<KisEnvironment, KisApiCredential> credentials) {
        return (KisCredentialRepository) Proxy.newProxyInstance(
                KisCredentialRepository.class.getClassLoader(),
                new Class<?>[]{KisCredentialRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findById")) {
                        return Optional.ofNullable(credentials.get(args[0]));
                    }
                    if (method.getName().equals("toString")) return "StubKisCredentialRepository";
                    return null;
                });
    }
}
