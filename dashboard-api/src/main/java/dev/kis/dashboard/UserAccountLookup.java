package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.auth.KisAccount;

import java.util.Optional;

/** Looks up a login user's own KIS account. */
@FunctionalInterface
public interface UserAccountLookup {
    Optional<KisAccount> find(String userKey, KisEnvironment environment);
}
