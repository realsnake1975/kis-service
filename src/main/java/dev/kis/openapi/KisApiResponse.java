package dev.kis.openapi;

import tools.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A KIS REST response body. Paged inquiries return their continuation keys
 * ({@code ctx_area_fk100}/{@code ctx_area_nk100}) at the top level of the body.
 */
public record KisApiResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") JsonNode output,
        @JsonProperty("output1") JsonNode output1,
        @JsonProperty("output2") JsonNode output2,
        @JsonProperty("ctx_area_fk100") String ctxAreaFk100,
        @JsonProperty("ctx_area_nk100") String ctxAreaNk100) {

    public KisApiResponse(String rtCd, String msgCd, String msg1, JsonNode output, JsonNode output1, JsonNode output2) {
        this(rtCd, msgCd, msg1, output, output1, output2, null, null);
    }

    public boolean isSuccess() {
        return "0".equals(rtCd);
    }
}
