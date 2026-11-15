package dev.kis.openapi.internal;

import dev.kis.openapi.KisApiException;
import dev.kis.openapi.KisApiPage;
import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import dev.kis.openapi.auth.KisAccessToken;
import dev.kis.openapi.auth.KisTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Component
public class KisRestClient implements KisApiTransport {
    private final WebClient.Builder webClientBuilder;
    private final KisTokenProvider tokenProvider;
    private final KisProperties properties;

    public KisRestClient(WebClient.Builder webClientBuilder, KisTokenProvider tokenProvider, KisProperties properties) {
        this.webClientBuilder = webClientBuilder;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
    }

    @Override
    public Mono<KisApiResponse> get(String path, String trId, Map<String, String> query) {
        return execute(path, trId, query, null, "").map(KisApiPage::data);
    }

    @Override
    public Mono<KisApiPage> getPage(String path, String trId, Map<String, String> query, String trCont) {
        return execute(path, trId, query, null, trCont);
    }

    @Override
    public Mono<KisApiResponse> post(String path, String trId, Map<String, ?> body) {
        return execute(path, trId, null, body, "").map(KisApiPage::data);
    }

    private Mono<KisApiPage> execute(String path, String trId, Map<String, String> query,
                                     Map<String, ?> body, String requestTrCont) {
        KisEnvironment environment = properties.getEnvironment();
        return Mono.fromCallable(() -> tokenProvider.getAccessToken(environment))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(token -> {
                    WebClient.RequestBodySpec request = webClientBuilder.clone().build()
                            .method(body == null ? org.springframework.http.HttpMethod.GET : org.springframework.http.HttpMethod.POST)
                            .uri(uriBuilder -> {
                                var builder = uriBuilder.scheme(environment.baseUrl().startsWith("https") ? "https" : "http")
                                        .host(host(environment.baseUrl())).port(port(environment.baseUrl())).path(path);
                                if (query != null) query.forEach(builder::queryParam);
                                return builder.build();
                            })
                            .headers(headers -> applyHeaders(headers, token, trId, requestTrCont));
                    if (body != null) request.contentType(MediaType.APPLICATION_JSON);
                    return (body == null ? request : request.bodyValue(body))
                            .retrieve()
                            .toEntity(KisApiResponse.class)
                            .flatMap(entity -> {
                                KisApiResponse response = entity.getBody();
                                if (response == null) {
                                    return Mono.error(new KisApiException(null, "KIS returned an empty response"));
                                }
                                KisApiPage page = new KisApiPage(response, entity.getHeaders().getFirst("tr_cont"));
                                return response.isSuccess()
                                        ? Mono.just(page)
                                        : Mono.error(new KisApiException(response.msgCd(), response.msg1()));
                            });
                })
                .onErrorMap(error -> error instanceof KisApiException ? error
                        : new KisApiException("KIS REST request failed for " + path, error));
    }

    private void applyHeaders(HttpHeaders headers, KisAccessToken token, String trId, String requestTrCont) {
        headers.setBearerAuth(token.accessToken());
        KisProperties.Account account = properties.account(properties.getEnvironment());
        headers.set("appkey", account.getAppKey());
        headers.set("appsecret", account.getAppSecret());
        headers.set("tr_id", trId);
        headers.set("tr_cont", requestTrCont == null ? "" : requestTrCont);
        headers.set("custtype", "P");
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    private String host(String baseUrl) {
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl).build().getHost();
    }

    private int port(String baseUrl) {
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl).build().getPort();
    }
}
