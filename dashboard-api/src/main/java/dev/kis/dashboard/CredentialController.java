package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import dev.kis.openapi.auth.KisAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;

/** The signed-in user's own KIS credentials. Secrets are write-only: they are never returned. */
@RestController
public class CredentialController {
    private final KisUserAccounts accounts;
    private final KisProperties properties;

    public CredentialController(KisUserAccounts accounts, KisProperties properties) {
        this.accounts = accounts;
        this.properties = properties;
    }

    @GetMapping("/api/admin/credentials/{environment}")
    Mono<CredentialStatus> get(@PathVariable KisEnvironment environment, Authentication authentication) {
        String userKey = UserKeys.of(authentication);
        return Mono.fromCallable(() -> status(environment, accounts.find(userKey, environment)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/api/admin/credentials/{environment}")
    Mono<CredentialStatus> save(@PathVariable KisEnvironment environment, @RequestBody KisUserAccounts.CredentialInput input,
                                Authentication authentication) {
        String userKey = UserKeys.of(authentication);
        return Mono.fromCallable(() -> status(environment, Optional.of(accounts.save(userKey, environment, input))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/api/admin/credentials/{environment}")
    Mono<ResponseEntity<Void>> delete(@PathVariable KisEnvironment environment, Authentication authentication) {
        String userKey = UserKeys.of(authentication);
        return Mono.fromCallable(() -> accounts.delete(userKey, environment)
                        ? ResponseEntity.noContent().<Void>build() : ResponseEntity.notFound().<Void>build())
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Validation reasons are written by us, so it is safe to show them to the user. */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> handle(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", error.getReason() == null ? "" : error.getReason()));
    }

    private CredentialStatus status(KisEnvironment environment, Optional<KisAccount> account) {
        boolean active = environment == properties.getEnvironment();
        return account.map(a -> new CredentialStatus(environment.name(), true, maskKey(a.appKey()),
                        DashboardController.maskAccount(a.accountNumber()), a.accountProductCode(), active))
                .orElseGet(() -> new CredentialStatus(environment.name(), false, "", "", "", active));
    }

    static String maskKey(String value) {
        return value == null || value.length() < 8 ? "••••••••" : value.substring(0, 4) + "••••" + value.substring(value.length() - 4);
    }

    public record CredentialStatus(String environment, boolean configured, String appKey, String accountNumber,
                                   String accountProductCode, boolean active) { }
}
