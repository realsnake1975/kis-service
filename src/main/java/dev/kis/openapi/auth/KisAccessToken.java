package dev.kis.openapi.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisAccessToken(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("access_token_token_expired") String expiresAt) {
}
