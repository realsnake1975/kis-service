package dev.kis.openapi.auth;

import dev.kis.openapi.KisApiException;
import dev.kis.openapi.KisEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class KisTokenProvider {
    private final WebClient.Builder webClientBuilder;
    private final KisCredentialProvider credentialProvider;

    /**
     * Spring Cache stores a token per environment. Cache expiry is configured with a safety
     * margin shorter than KIS's documented one-day token lifetime.
     */
    @Cacheable(cacheNames = "kisAccessTokens", key = "#p0.name()", sync = true)
    public KisAccessToken getAccessToken(KisEnvironment environment) {
        KisApiCredentials credentials = credentialProvider.getCredentials(environment);
        try {
            KisAccessToken token = webClientBuilder.clone().build()
                    .post()
                    .uri(environment.baseUrl() + "/oauth2/tokenP")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "grant_type", "client_credentials",
                            "appkey", credentials.appKey(),
                            "appsecret", credentials.appSecret()))
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
}
