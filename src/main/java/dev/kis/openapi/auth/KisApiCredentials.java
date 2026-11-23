package dev.kis.openapi.auth;

public record KisApiCredentials(String appKey, String appSecret) {
    @Override
    public String toString() {
        return "KisApiCredentials[appKey=****, appSecret=****]";
    }
}
