package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.auth.KisAccount;
import dev.kis.openapi.auth.KisAccountSource;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

/**
 * Per-user KIS credentials stored in the database. As a {@link KisAccountSource} it also supplies the default account
 * for calls made without a user (the background price monitor): the first user who registered credentials.
 * <p>
 * Decrypting a row costs about a second (three Jasypt columns at 100,000 key-obtention iterations each), so decrypted
 * accounts are cached in memory. Saving or deleting clears the cache; entries also expire after {@link #CACHE_TTL} in
 * case the table is changed outside this application.
 */
@Service
public class KisUserAccounts implements KisAccountSource, UserAccountLookup {
    static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final KisUserCredentialRepository repository;
    private final Cache<UserEnvironment, Optional<KisAccount>> byUser;
    private final Cache<KisEnvironment, Optional<KisAccount>> defaults;

    @Autowired
    public KisUserAccounts(KisUserCredentialRepository repository) {
        this(repository, Ticker.systemTicker());
    }

    KisUserAccounts(KisUserCredentialRepository repository, Ticker ticker) {
        this.repository = repository;
        this.byUser = Caffeine.newBuilder().ticker(ticker).expireAfterWrite(CACHE_TTL).maximumSize(1_000).build();
        this.defaults = Caffeine.newBuilder().ticker(ticker).expireAfterWrite(CACHE_TTL).maximumSize(10).build();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KisAccount> find(String userKey, KisEnvironment environment) {
        return byUser.get(new UserEnvironment(userKey, environment),
                key -> repository.findByUserKeyAndEnvironment(userKey, environment).map(KisUserCredential::toAccount));
    }

    /** Saves the user's credentials. A blank secret keeps the stored one, so other fields can change without it. */
    @Transactional
    public KisAccount save(String userKey, KisEnvironment environment, CredentialInput input) {
        String appKey = trim(input.appKey());
        String appSecret = trim(input.appSecret());
        String accountNumber = trim(input.accountNumber()).replace("-", "");
        String productCode = trim(input.accountProductCode()).isEmpty() ? "01" : trim(input.accountProductCode());
        if (appKey.isEmpty()) throw badRequest("App Key를 입력하세요.");
        if (!accountNumber.matches("\\d{8}")) throw badRequest("계좌번호는 앞 8자리 숫자로 입력하세요.");
        if (!productCode.matches("\\d{2}")) throw badRequest("계좌상품코드는 2자리 숫자로 입력하세요.");
        KisUserCredential credential = repository.findByUserKeyAndEnvironment(userKey, environment)
                .orElseGet(() -> new KisUserCredential(userKey, environment));
        if (appSecret.isEmpty()) {
            if (credential.getAppSecret() == null || credential.getAppSecret().isBlank()) throw badRequest("App Secret을 입력하세요.");
            appSecret = credential.getAppSecret();
        }
        credential.update(appKey, appSecret, accountNumber, productCode);
        KisAccount saved = repository.save(credential).toAccount();
        invalidate(userKey, environment);
        return saved;
    }

    @Transactional
    public boolean delete(String userKey, KisEnvironment environment) {
        boolean deleted = repository.findByUserKeyAndEnvironment(userKey, environment).map(credential -> {
            repository.delete(credential);
            return true;
        }).orElse(false);
        invalidate(userKey, environment);
        return deleted;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KisAccount> defaultAccount(KisEnvironment environment) {
        return defaults.get(environment,
                key -> repository.findFirstByEnvironmentOrderByCreatedAtAscIdAsc(environment).map(KisUserCredential::toAccount));
    }

    /** Called after a write; the default (first registered) account may have changed too. */
    private void invalidate(String userKey, KisEnvironment environment) {
        Runnable clear = () -> {
            byUser.invalidate(new UserEnvironment(userKey, environment));
            defaults.invalidate(environment);
        };
        clear.run();
        // Clear again after commit so a read between the write and the commit cannot re-cache the old row.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear.run();
                }
            });
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record UserEnvironment(String userKey, KisEnvironment environment) { }

    public record CredentialInput(String appKey, String appSecret, String accountNumber, String accountProductCode) {
        @Override
        public String toString() {
            return "CredentialInput[****]";
        }
    }
}
