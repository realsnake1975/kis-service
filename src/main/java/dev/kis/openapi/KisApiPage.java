package dev.kis.openapi;

import tools.jackson.databind.JsonNode;

/** A KIS response body plus the continuation header returned by the REST API. */
public record KisApiPage(KisApiResponse data, String trCont) {
    /** KIS answers {@code tr_cont} {@code F} or {@code M} when another page follows. */
    public boolean hasNextPage() {
        return ("F".equalsIgnoreCase(trCont) || "M".equalsIgnoreCase(trCont))
                && !(continuationSearchKey().isBlank() && continuationKey().isBlank());
    }

    public String continuationSearchKey() {
        return continuationValue(data == null ? null : data.ctxAreaFk100(), "ctx_area_fk100");
    }

    public String continuationKey() {
        return continuationValue(data == null ? null : data.ctxAreaNk100(), "ctx_area_nk100");
    }

    // The keys live at the top level of the body; output2 is only a fallback for older responses.
    private String continuationValue(String topLevel, String field) {
        if (topLevel != null && !topLevel.isBlank()) return topLevel.strip();
        JsonNode output2 = data == null ? null : data.output2();
        if (output2 != null && output2.isArray()) output2 = output2.isEmpty() ? null : output2.get(0);
        return output2 == null ? "" : output2.path(field).asString("").strip();
    }
}
