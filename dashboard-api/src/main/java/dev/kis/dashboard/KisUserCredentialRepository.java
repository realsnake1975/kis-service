package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KisUserCredentialRepository extends JpaRepository<KisUserCredential, Long> {
    Optional<KisUserCredential> findByUserKeyAndEnvironment(String userKey, KisEnvironment environment);

    /** The first user to register credentials for an environment owns the background price monitor. */
    Optional<KisUserCredential> findFirstByEnvironmentOrderByCreatedAtAscIdAsc(KisEnvironment environment);
}
