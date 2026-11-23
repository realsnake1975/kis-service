package dev.kis.openapi.auth;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KisTokenProviderTest {
    @Test
    void sendsConfiguredCredentialsInTheTokenRequestBody() {
        AtomicReference<String> requestUrl = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        WebClient.Builder client = WebClient.builder().exchangeFunction(request -> {
            requestUrl.set(request.url().toString());
            MockClientHttpRequest bodyRequest = new MockClientHttpRequest(request.method(), request.url());
            BodyInserter.Context context = new BodyInserter.Context() {
                @Override
                public List<HttpMessageWriter<?>> messageWriters() {
                    return ExchangeStrategies.withDefaults().messageWriters();
                }

                @Override
                public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                    return Optional.empty();
                }

                @Override
                public Map<String, Object> hints() {
                    return Map.of();
                }
            };
            request.body().insert(bodyRequest, context).block();
            requestBody.set(bodyRequest.getBodyAsString().block());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"access_token\":\"access-token\",\"token_type\":\"Bearer\",\"access_token_token_expired\":\"2099-01-01 00:00:00\"}")
                    .build());
        });
        KisProperties properties = new KisProperties();
        properties.getPaper().setAppKey("cfg-paper-key");
        properties.getPaper().setAppSecret("cfg-paper-secret");
        KisTokenProvider provider = new KisTokenProvider(client, new KisCredentialProvider(properties));

        KisAccessToken token = provider.getAccessToken(KisEnvironment.PAPER);

        assertEquals("access-token", token.accessToken());
        assertEquals(KisEnvironment.PAPER.baseUrl() + "/oauth2/tokenP", requestUrl.get());
        assertTrue(requestBody.get().contains("\"appkey\":\"cfg-paper-key\""));
        assertTrue(requestBody.get().contains("\"appsecret\":\"cfg-paper-secret\""));
    }
}
