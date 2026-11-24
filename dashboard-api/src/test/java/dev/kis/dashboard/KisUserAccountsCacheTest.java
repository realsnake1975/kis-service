package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each repository read stands for a slow decrypt; the cache must avoid repeating it. */
class KisUserAccountsCacheTest {
    private final List<KisUserCredential> rows = new ArrayList<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicLong nanos = new AtomicLong();
    private final KisUserAccounts accounts = new KisUserAccounts(repository(), nanos::get);

    private KisUserCredentialRepository repository() {
        return (KisUserCredentialRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{KisUserCredentialRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findByUserKeyAndEnvironment" -> {
                        reads.incrementAndGet();
                        yield rows.stream().filter(r -> r.getUserKey().equals(args[0]) && r.getEnvironment() == args[1]).findFirst();
                    }
                    case "findFirstByEnvironmentOrderByCreatedAtAscIdAsc" -> {
                        reads.incrementAndGet();
                        yield rows.stream().filter(r -> r.getEnvironment() == args[0]).findFirst();
                    }
                    case "save" -> {
                        if (!rows.contains(args[0])) rows.add((KisUserCredential) args[0]);
                        yield args[0];
                    }
                    case "delete" -> {
                        rows.remove(args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private void save(String user, String key, String account) {
        accounts.save(user, KisEnvironment.REAL, new KisUserAccounts.CredentialInput(key, "secret", account, "01"));
    }

    @Test
    void repeatedLookupsReadTheRowOnce() {
        save("github:1", "PSkey0000000001", "11112222");
        reads.set(0);

        for (int i = 0; i < 5; i++) accounts.find("github:1", KisEnvironment.REAL);
        for (int i = 0; i < 5; i++) accounts.defaultAccount(KisEnvironment.REAL);

        assertEquals(2, reads.get()); // one per cache: by user, and default account
    }

    @Test
    void savingReplacesTheCachedAccount() {
        save("github:1", "PSkey0000000001", "11112222");
        accounts.find("github:1", KisEnvironment.REAL);
        accounts.defaultAccount(KisEnvironment.REAL);

        save("github:1", "PSkey0000000002", "33334444");

        assertEquals("PSkey0000000002", accounts.find("github:1", KisEnvironment.REAL).orElseThrow().appKey());
        assertEquals("33334444", accounts.defaultAccount(KisEnvironment.REAL).orElseThrow().accountNumber());
    }

    @Test
    void anUnregisteredUserIsCachedAsEmptyUntilTheyRegister() {
        assertTrue(accounts.find("github:2", KisEnvironment.REAL).isEmpty());
        assertTrue(accounts.defaultAccount(KisEnvironment.REAL).isEmpty());

        save("github:2", "PSkey0000000009", "55556666");

        assertEquals("PSkey0000000009", accounts.find("github:2", KisEnvironment.REAL).orElseThrow().appKey());
        assertEquals("PSkey0000000009", accounts.defaultAccount(KisEnvironment.REAL).orElseThrow().appKey());
    }

    @Test
    void deletingClearsTheCachedAccount() {
        save("github:1", "PSkey0000000001", "11112222");
        accounts.find("github:1", KisEnvironment.REAL);
        accounts.defaultAccount(KisEnvironment.REAL);

        accounts.delete("github:1", KisEnvironment.REAL);

        assertEquals(Optional.empty(), accounts.find("github:1", KisEnvironment.REAL));
        assertEquals(Optional.empty(), accounts.defaultAccount(KisEnvironment.REAL));
    }

    @Test
    void entriesExpireSoOutsideChangesAreEventuallySeen() {
        save("github:1", "PSkey0000000001", "11112222");
        accounts.find("github:1", KisEnvironment.REAL);
        reads.set(0);

        nanos.addAndGet(KisUserAccounts.CACHE_TTL.minus(Duration.ofSeconds(1)).toNanos());
        accounts.find("github:1", KisEnvironment.REAL);
        assertEquals(0, reads.get());

        nanos.addAndGet(Duration.ofSeconds(2).toNanos());
        accounts.find("github:1", KisEnvironment.REAL);
        assertEquals(1, reads.get());
    }
}
