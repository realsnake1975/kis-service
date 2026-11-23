package dev.kis.openapi.auth;

import dev.kis.openapi.KisEnvironment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "kis_api_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KisApiCredential {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 10)
    private KisEnvironment environment;

    @Column(name = "app_key", nullable = false, length = 256)
    private String appKey;

    @Column(name = "app_secret", nullable = false, length = 512)
    private String appSecret;

    public static KisApiCredential create(KisEnvironment environment, String appKey, String appSecret) {
        KisApiCredential credential = new KisApiCredential();
        credential.environment = environment;
        credential.appKey = appKey;
        credential.appSecret = appSecret;
        return credential;
    }
}
