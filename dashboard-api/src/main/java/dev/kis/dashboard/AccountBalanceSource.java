package dev.kis.dashboard;

import dev.kis.openapi.KisApiResponse;
import reactor.core.publisher.Mono;

/** Read-only source of the domestic stock balance (KIS inquire-balance). */
@FunctionalInterface
public interface AccountBalanceSource {
    Mono<KisApiResponse> balance();
}
