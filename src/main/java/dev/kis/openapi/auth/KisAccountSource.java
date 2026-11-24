package dev.kis.openapi.auth;

import dev.kis.openapi.KisEnvironment;

import java.util.Optional;

/**
 * Supplies the default account for calls made without a {@link KisAccountContext} account, such as the background
 * price monitor. When no source returns an account, {@code kis.openapi.<environment>.*} properties are used.
 */
@FunctionalInterface
public interface KisAccountSource {
    Optional<KisAccount> defaultAccount(KisEnvironment environment);
}
