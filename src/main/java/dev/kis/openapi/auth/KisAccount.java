package dev.kis.openapi.auth;

/** App credentials plus the trading account they belong to. */
public record KisAccount(String appKey, String appSecret, String accountNumber, String accountProductCode) {
    public KisAccount {
        appKey = strip(appKey);
        appSecret = strip(appSecret);
        accountNumber = strip(accountNumber);
        accountProductCode = accountProductCode == null || accountProductCode.isBlank() ? "01" : accountProductCode.strip();
    }

    public KisApiCredentials credentials() {
        return new KisApiCredentials(appKey, appSecret);
    }

    public boolean hasCredentials() {
        return !appKey.isEmpty() && !appSecret.isEmpty();
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    @Override
    public String toString() {
        return "KisAccount[appKey=****, appSecret=****, accountNumber=****, accountProductCode=" + accountProductCode + "]";
    }
}
