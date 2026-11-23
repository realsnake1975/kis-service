package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiResponse;

import java.time.Instant;

/** Published after each scheduled price lookup, including failed lookups. */
public record DomesticStockPriceEvent(
        String symbol,
        Instant observedAt,
        KisApiResponse response,
        String errorMessage) {

    public boolean isSuccess() {
        return response != null && response.isSuccess() && errorMessage == null;
    }
}
