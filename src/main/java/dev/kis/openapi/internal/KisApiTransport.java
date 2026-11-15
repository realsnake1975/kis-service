package dev.kis.openapi.internal;

import dev.kis.openapi.KisApiPage;
import dev.kis.openapi.KisApiResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

/** Transport boundary for the domestic-stock client. */
public interface KisApiTransport {
    Mono<KisApiResponse> get(String path, String trId, Map<String, String> query);
    Mono<KisApiPage> getPage(String path, String trId, Map<String, String> query, String trCont);
    Mono<KisApiResponse> post(String path, String trId, Map<String, ?> body);
}
