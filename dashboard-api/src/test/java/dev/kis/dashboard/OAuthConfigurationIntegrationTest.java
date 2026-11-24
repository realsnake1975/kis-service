package dev.kis.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void logoutReturnsOkInsteadOfRedirectingForTheSpa() {
        var csrf = webClient.get().uri("/api/session/csrf").exchange()
                .expectStatus().isOk()
                .returnResult(Map.class);
        var cookie = csrf.getResponseCookies().getFirst("XSRF-TOKEN");
        assertNotNull(cookie);
        Map<?, ?> body = csrf.getResponseBody().blockFirst();
        assertNotNull(body);

        webClient.post().uri("/logout")
                .cookie("XSRF-TOKEN", cookie.getValue())
                .header((String) body.get("headerName"), (String) body.get("token"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void logoutWithoutCsrfTokenIsRejected() {
        webClient.post().uri("/logout").exchange().expectStatus().isForbidden();
    }
}
