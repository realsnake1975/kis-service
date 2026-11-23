package dev.kis.openapi.auth;

import dev.kis.openapi.KisEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KisCredentialRepository extends JpaRepository<KisApiCredential, KisEnvironment> {
}
