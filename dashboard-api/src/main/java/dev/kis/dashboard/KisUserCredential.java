package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.auth.KisAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** A login user's KIS app credentials and account for one environment. Secrets are encrypted at rest. */
@Entity
@Table(name = "kis_user_credential", uniqueConstraints = @UniqueConstraint(columnNames = {"user_key", "environment"}))
public class KisUserCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 200)
    private String userKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 10)
    private KisEnvironment environment;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "app_key", nullable = false, length = 1024)
    private String appKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "app_secret", nullable = false, length = 2048)
    private String appSecret;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number", nullable = false, length = 512)
    private String accountNumber;

    @Column(name = "account_product_code", nullable = false, length = 2)
    private String accountProductCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KisUserCredential() {
    }

    public KisUserCredential(String userKey, KisEnvironment environment) {
        this.userKey = userKey;
        this.environment = environment;
        this.createdAt = Instant.now();
    }

    public void update(String appKey, String appSecret, String accountNumber, String accountProductCode) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.accountNumber = accountNumber;
        this.accountProductCode = accountProductCode;
        this.updatedAt = Instant.now();
    }

    public KisAccount toAccount() {
        return new KisAccount(appKey, appSecret, accountNumber, accountProductCode);
    }

    public String getUserKey() { return userKey; }
    public KisEnvironment getEnvironment() { return environment; }
    public String getAppSecret() { return appSecret; }
    public Instant getUpdatedAt() { return updatedAt; }
}
