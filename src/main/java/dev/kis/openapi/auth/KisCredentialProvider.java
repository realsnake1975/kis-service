package dev.kis.openapi.auth;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the KIS account for a call: the {@link KisAccountContext} account if present, otherwise the first
 * {@link KisAccountSource} default, otherwise {@code kis.openapi.paper.*} / {@code kis.openapi.real.*}.
 * There is no fallback between environments.
 */
@Component
public class KisCredentialProvider {
    private final KisProperties properties;
    private final List<KisAccountSource> sources;

    public KisCredentialProvider(KisProperties properties) {
        this(properties, List.of());
    }

    public KisCredentialProvider(KisProperties properties, List<KisAccountSource> sources) {
        this.properties = properties;
        this.sources = List.copyOf(sources);
    }

    @Autowired
    public KisCredentialProvider(KisProperties properties, ObjectProvider<KisAccountSource> sources) {
        this(properties, sources.orderedStream().toList());
    }

    /** The account for a call: context account first, then the default account. */
    public KisAccount resolve(ContextView context, KisEnvironment environment) {
        return KisAccountContext.from(context).orElseGet(() -> defaultAccount(environment));
    }

    /**
     * Like {@link #resolve} but without requiring app credentials, for building account-specific requests
     * (credentials are checked when the request is sent).
     */
    public KisAccount resolveAccount(ContextView context, KisEnvironment environment) {
        return KisAccountContext.from(context).or(() -> sourceAccount(environment)).orElseGet(() -> configuredAccount(environment));
    }

    public KisAccount defaultAccount(KisEnvironment environment) {
        Optional<KisAccount> fromSource = sourceAccount(environment);
        if (fromSource.isPresent()) return fromSource.get();
        KisAccount configured = configuredAccount(environment);
        if (!configured.hasCredentials()) {
            throw new IllegalStateException("KIS app credentials are not configured for environment " + environment
                    + " (kis.openapi." + environment.name().toLowerCase() + ".app-key / app-secret)");
        }
        return configured;
    }

    public KisApiCredentials getCredentials(KisEnvironment environment) {
        return defaultAccount(environment).credentials();
    }

    public boolean isConfigured(KisEnvironment environment) {
        try {
            defaultAccount(environment);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private Optional<KisAccount> sourceAccount(KisEnvironment environment) {
        for (KisAccountSource source : sources) {
            Optional<KisAccount> account = source.defaultAccount(environment).filter(KisAccount::hasCredentials);
            if (account.isPresent()) return account;
        }
        return Optional.empty();
    }

    private KisAccount configuredAccount(KisEnvironment environment) {
        KisProperties.Account account = properties.account(environment);
        return new KisAccount(account.getAppKey(), account.getAppSecret(), account.getAccountNumber(), account.getAccountProductCode());
    }
}
