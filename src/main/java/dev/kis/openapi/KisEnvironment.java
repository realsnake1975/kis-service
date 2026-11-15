package dev.kis.openapi;

public enum KisEnvironment {
    PAPER("https://openapivts.koreainvestment.com:29443"),
    REAL("https://openapi.koreainvestment.com:9443");

    private final String baseUrl;

    KisEnvironment(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }
}
