package dev.kis.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:kis-dashboard-oauth;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "kis.openapi.domestic-stock-monitor.enabled=false"
})
@AutoConfigureWebTestClient
@EnabledIfEnvironmentVariable(named = "JASYPT_ENCRYPTOR_PASSWORD", matches = ".+")
class OAuthConfigurationIntegrationTest {
    @Autowired
    private WebTestClient webClient;

    @Test
    void googleLoginStartsOAuthRedirect() {
        webClient.get().uri("/oauth2/authorization/google")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", location ->
                        assertTrue(location.startsWith("https://accounts.google.com/")));
    }

    @Test
    void githubLoginStartsOAuthRedirect() {
        webClient.get().uri("/oauth2/authorization/github")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", location ->
                        assertTrue(location.startsWith("https://github.com/login/oauth/authorize")));
    }
}
