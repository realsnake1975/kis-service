package dev.kis.openapi.internal;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import dev.kis.openapi.auth.KisAccessToken;
import dev.kis.openapi.auth.KisCredentialProvider;
import dev.kis.openapi.auth.KisTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KisRestClientCredentialTest {
    @Test
    void usesConfiguredCredentialsInEveryRestRequestHeader() {
        AtomicReference<org.springframework.web.reactive.function.client.ClientRequest> captured = new AtomicReference<>();
        WebClient.Builder client = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"rt_cd\":\"0\",\"msg_cd\":\"0\",\"msg1\":\"ok\",\"output\":{},\"output1\":null,\"output2\":null}")
                    .build());
        });
        KisProperties properties = new KisProperties();
        properties.setEnvironment(KisEnvironment.PAPER);
        properties.getPaper().setAppKey("cfg-paper-key");
        properties.getPaper().setAppSecret("cfg-paper-secret");
        KisCredentialProvider credentials = new KisCredentialProvider(properties);
        KisTokenProvider tokens = new KisTokenProvider(client, credentials) {
            @Override
            public KisAccessToken getAccessToken(KisEnvironment environment, dev.kis.openapi.auth.KisApiCredentials credentials) {
                return new KisAccessToken("cached-paper-token", "Bearer", "2099-01-01 00:00:00");
            }
        };
        KisRestClient restClient = new KisRestClient(client, tokens, credentials, properties);

        KisApiResponse response = restClient.get("/test", "TEST0001R", java.util.Map.of())
                .block(Duration.ofSeconds(3));

        assertTrue(response.isSuccess());
        assertEquals("cfg-paper-key", captured.get().headers().getFirst("appkey"));
        assertEquals("cfg-paper-secret", captured.get().headers().getFirst("appsecret"));
        assertEquals("Bearer cached-paper-token", captured.get().headers().getFirst("Authorization"));
    }

    @Test
    void usesTheReactorContextAccountInsteadOfConfiguration() {
        AtomicReference<org.springframework.web.reactive.function.client.ClientRequest> captured = new AtomicReference<>();
        AtomicReference<String> tokenAppKey = new AtomicReference<>();
        WebClient.Builder client = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"rt_cd\":\"0\",\"msg_cd\":\"0\",\"msg1\":\"ok\"}")
                    .build());
        });
        KisProperties properties = new KisProperties();
        properties.setEnvironment(KisEnvironment.REAL);
        properties.getReal().setAppKey("cfg-key");
        properties.getReal().setAppSecret("cfg-secret");
        KisCredentialProvider credentials = new KisCredentialProvider(properties);
        KisTokenProvider tokens = new KisTokenProvider(client, credentials) {
            @Override
            public KisAccessToken getAccessToken(KisEnvironment environment, dev.kis.openapi.auth.KisApiCredentials used) {
                tokenAppKey.set(used.appKey());
                return new KisAccessToken("user-token", "Bearer", "2099-01-01 00:00:00");
            }
        };
        KisRestClient restClient = new KisRestClient(client, tokens, credentials, properties);
        var account = new dev.kis.openapi.auth.KisAccount("user-key", "user-secret", "33334444", "01");

        restClient.get("/test", "TEST0001R", java.util.Map.of())
                .contextWrite(dev.kis.openapi.auth.KisAccountContext.with(account))
                .block(Duration.ofSeconds(3));

        assertEquals("user-key", tokenAppKey.get());
        assertEquals("user-key", captured.get().headers().getFirst("appkey"));
        assertEquals("user-secret", captured.get().headers().getFirst("appsecret"));
    }
}
