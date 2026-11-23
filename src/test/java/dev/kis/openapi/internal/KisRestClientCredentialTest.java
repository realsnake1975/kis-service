package dev.kis.openapi.internal;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import dev.kis.openapi.auth.KisAccessToken;
import dev.kis.openapi.auth.KisApiCredential;
import dev.kis.openapi.auth.KisCredentialProvider;
import dev.kis.openapi.auth.KisCredentialRepository;
import dev.kis.openapi.auth.KisTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KisRestClientCredentialTest {
    @Test
    void usesDatabaseCredentialsInEveryRestRequestHeader() {
        AtomicReference<org.springframework.web.reactive.function.client.ClientRequest> captured = new AtomicReference<>();
        WebClient.Builder client = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"rt_cd\":\"0\",\"msg_cd\":\"0\",\"msg1\":\"ok\",\"output\":{},\"output1\":null,\"output2\":null}")
                    .build());
        });
        KisCredentialProvider credentials = new KisCredentialProvider(repository());
        KisTokenProvider tokens = new KisTokenProvider(client, credentials) {
            @Override
            public KisAccessToken getAccessToken(KisEnvironment environment) {
                return new KisAccessToken("cached-paper-token", "Bearer", "2099-01-01 00:00:00");
            }
        };
        KisProperties properties = new KisProperties();
        properties.setEnvironment(KisEnvironment.PAPER);
        KisRestClient restClient = new KisRestClient(client, tokens, credentials, properties);

        KisApiResponse response = restClient.get("/test", "TEST0001R", java.util.Map.of())
                .block(Duration.ofSeconds(3));

        assertTrue(response.isSuccess());
        assertEquals("db-paper-key", captured.get().headers().getFirst("appkey"));
        assertEquals("db-paper-secret", captured.get().headers().getFirst("appsecret"));
        assertEquals("Bearer cached-paper-token", captured.get().headers().getFirst("Authorization"));
    }

    private KisCredentialRepository repository() {
        KisApiCredential paper = KisApiCredential.create(KisEnvironment.PAPER, "db-paper-key", "db-paper-secret");
        return (KisCredentialRepository) Proxy.newProxyInstance(
                KisCredentialRepository.class.getClassLoader(),
                new Class<?>[]{KisCredentialRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findById")) {
                        return args[0] == KisEnvironment.PAPER ? Optional.of(paper) : Optional.empty();
                    }
                    if (method.getName().equals("toString")) return "StubKisCredentialRepository";
                    return null;
                });
    }
}
