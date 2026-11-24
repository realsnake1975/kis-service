package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.auth.KisAccount;
import dev.kis.openapi.auth.KisAccountSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Per-user KIS credentials stored in the database. As a {@link KisAccountSource} it also supplies the default account
 * for calls made without a user (the background price monitor): the first user who registered credentials.
 */
@Service
public class KisUserAccounts implements KisAccountSource, UserAccountLookup {
    private final KisUserCredentialRepository repository;

    public KisUserAccounts(KisUserCredentialRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KisAccount> find(String userKey, KisEnvironment environment) {
        return repository.findByUserKeyAndEnvironment(userKey, environment).map(KisUserCredential::toAccount);
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
        return repository.save(credential).toAccount();
    }

    @Transactional
    public boolean delete(String userKey, KisEnvironment environment) {
        return repository.findByUserKeyAndEnvironment(userKey, environment).map(credential -> {
            repository.delete(credential);
            return true;
        }).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KisAccount> defaultAccount(KisEnvironment environment) {
        return repository.findFirstByEnvironmentOrderByCreatedAtAscIdAsc(environment).map(KisUserCredential::toAccount);
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record CredentialInput(String appKey, String appSecret, String accountNumber, String accountProductCode) {
        @Override
        public String toString() {
            return "CredentialInput[****]";
        }
    }
}
