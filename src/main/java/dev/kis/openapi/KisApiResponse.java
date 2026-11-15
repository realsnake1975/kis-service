package dev.kis.openapi;

import tools.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;

public record KisApiResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") JsonNode output,
        @JsonProperty("output1") JsonNode output1,
        @JsonProperty("output2") JsonNode output2) {
    public boolean isSuccess() {
        return "0".equals(rtCd);
    }
}
