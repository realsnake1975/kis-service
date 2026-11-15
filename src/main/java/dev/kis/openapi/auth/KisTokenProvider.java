package dev.kis.openapi.auth;

import dev.kis.openapi.KisApiException;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class KisTokenProvider {
    private final WebClient.Builder webClientBuilder;
    private final KisProperties properties;

    public KisTokenProvider(WebClient.Builder webClientBuilder, KisProperties properties) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
    }

    /**
     * Spring Cache stores a token per environment. Cache expiry is configured with a safety
     * margin shorter than KIS's documented one-day token lifetime.
     */
    @Cacheable(cacheNames = "kisAccessTokens", key = "#p0.name()", sync = true)
    public KisAccessToken getAccessToken(KisEnvironment environment) {
        KisProperties.Account account = properties.account(environment);
        requireCredentials(account);
        try {
            KisAccessToken token = webClientBuilder.clone().build()
                    .post()
                    .uri(environment.baseUrl() + "/oauth2/tokenP")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "grant_type", "client_credentials",
                            "appkey", account.getAppKey(),
                            "appsecret", account.getAppSecret()))
                    .retrieve()
                    .bodyToMono(KisAccessToken.class)
                    .block();
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new KisApiException(null, "KIS token response did not contain an access token");
            }
            return token;
        } catch (KisApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KisApiException("Failed to obtain KIS access token", exception);
        }
    }

    private void requireCredentials(KisProperties.Account account) {
        if (account.getAppKey() == null || account.getAppKey().isBlank()
                || account.getAppSecret() == null || account.getAppSecret().isBlank()) {
            throw new IllegalStateException("Set app-key and app-secret for the selected kis.openapi environment");
        }
    }
}
