package dev.kis.openapi.auth;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Optional;

/**
 * Carries the account to use for KIS calls in the Reactor context, so one application can call KIS on behalf of
 * different users: {@code client.getBalance().contextWrite(KisAccountContext.with(account))}.
 */
public final class KisAccountContext {
    private static final Class<KisAccount> KEY = KisAccount.class;

    private KisAccountContext() {
    }

    public static Context with(KisAccount account) {
        return Context.of(KEY, account);
    }

    public static Optional<KisAccount> from(ContextView context) {
        return context.getOrEmpty(KEY);
    }
}
