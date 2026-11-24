package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.auth.KisAccount;
import dev.kis.openapi.auth.KisCredentialProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.util.context.Context;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kis-dashboard-credentials;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "kis.openapi.domestic-stock-monitor.enabled=false",
        // No credentials from configuration: everything must come from the database.
        "kis.openapi.real.app-key=",
        "kis.openapi.real.app-secret=",
        "kis.openapi.real.account-number="
})
@EnabledIfEnvironmentVariable(named = "JASYPT_ENCRYPTOR_PASSWORD", matches = ".+")
class KisUserAccountsIntegrationTest {
    @Autowired
    private KisUserAccounts accounts;
    @Autowired
    private KisUserCredentialRepository repository;
    @Autowired
    private KisCredentialProvider credentialProvider;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private KisUserAccounts.CredentialInput input(String key, String secret, String account) {
        return new KisUserAccounts.CredentialInput(key, secret, account, "01");
    }

    @Test
    void storesSecretsEncryptedAndReadsThemBackDecrypted() {
        accounts.save("github:1", KisEnvironment.REAL, input("PSownerappkey0001", "owner-secret-value", "1234-5678"));

        KisAccount account = accounts.find("github:1", KisEnvironment.REAL).orElseThrow();
        assertEquals("PSownerappkey0001", account.appKey());
        assertEquals("owner-secret-value", account.appSecret());
        assertEquals("12345678", account.accountNumber());

        Map<String, Object> row = jdbc.queryForMap("select app_key, app_secret, account_number from kis_user_credential");
        row.values().forEach(value -> {
            String stored = String.valueOf(value);
            assertFalse(stored.contains("PSownerappkey0001") || stored.contains("owner-secret-value") || stored.contains("12345678"),
                    "column must not hold plaintext");
        });
    }

    @Test
    void credentialsAreIsolatedPerUser() {
        accounts.save("github:1", KisEnvironment.REAL, input("PSownerappkey0001", "owner-secret", "11112222"));
        assertTrue(accounts.find("github:2", KisEnvironment.REAL).isEmpty());
        assertTrue(accounts.find("google:1", KisEnvironment.REAL).isEmpty());
        assertTrue(accounts.find("github:1", KisEnvironment.PAPER).isEmpty());
    }

    @Test
    void blankSecretKeepsTheStoredSecret() {
        accounts.save("github:1", KisEnvironment.REAL, input("PSownerappkey0001", "first-secret", "11112222"));
        accounts.save("github:1", KisEnvironment.REAL, input("PSownerappkey0002", "", "33334444"));

        KisAccount account = accounts.find("github:1", KisEnvironment.REAL).orElseThrow();
        assertEquals("PSownerappkey0002", account.appKey());
        assertEquals("first-secret", account.appSecret());
        assertEquals("33334444", account.accountNumber());
        assertEquals(1, repository.count());
    }

    @Test
    void newCredentialsRequireASecretAndValidAccountNumber() {
        assertThrows(ResponseStatusException.class,
                () -> accounts.save("github:1", KisEnvironment.REAL, input("PSkey", "", "11112222")));
        assertThrows(ResponseStatusException.class,
                () -> accounts.save("github:1", KisEnvironment.REAL, input("PSkey", "secret", "1234")));
    }

    @Test
    void firstRegisteredUserIsTheDefaultAccountForCallsWithoutAUser() {
        accounts.save("github:1", KisEnvironment.REAL, input("PSownerappkey0001", "owner-secret", "11112222"));
        accounts.save("github:2", KisEnvironment.REAL, input("PSotherappkey0002", "other-secret", "33334444"));

        assertEquals("PSownerappkey0001", credentialProvider.resolve(Context.empty(), KisEnvironment.REAL).appKey());
        assertFalse(credentialProvider.isConfigured(KisEnvironment.PAPER));
    }

    @Test
    void deleteRemovesOnlyThatUsersCredentials() {
        accounts.save("github:1", KisEnvironment.REAL, input("PSownerappkey0001", "owner-secret", "11112222"));
        accounts.save("github:2", KisEnvironment.REAL, input("PSotherappkey0002", "other-secret", "33334444"));

        assertTrue(accounts.delete("github:2", KisEnvironment.REAL));
        assertFalse(accounts.delete("github:2", KisEnvironment.REAL));
        assertTrue(accounts.find("github:1", KisEnvironment.REAL).isPresent());
    }
}
