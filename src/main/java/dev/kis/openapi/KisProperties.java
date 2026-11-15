package dev.kis.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kis.openapi")
public class KisProperties {
    private KisEnvironment environment = KisEnvironment.PAPER;
    private final Account paper = new Account();
    private final Account real = new Account();

    public KisEnvironment getEnvironment() { return environment; }
    public void setEnvironment(KisEnvironment environment) { this.environment = environment; }
    public Account getPaper() { return paper; }
    public Account getReal() { return real; }

    public Account account(KisEnvironment selected) {
        return selected == KisEnvironment.REAL ? real : paper;
    }

    public static class Account {
        private String appKey;
        private String appSecret;
        private String accountNumber;
        private String accountProductCode = "01";

        public String getAppKey() { return appKey; }
        public void setAppKey(String appKey) { this.appKey = appKey; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
        public String getAccountProductCode() { return accountProductCode; }
        public void setAccountProductCode(String accountProductCode) { this.accountProductCode = accountProductCode; }
    }
}
