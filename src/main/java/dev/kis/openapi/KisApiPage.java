package dev.kis.openapi;

/** A KIS response body plus the continuation header returned by the REST API. */
public record KisApiPage(KisApiResponse data, String trCont) {
}
