package dev.kis.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SessionControllerTest {
    private final SessionController controller = new SessionController();

    @Test
    void anonymousSessionIsReportedAsUnauthenticated() {
        var response = controller.session(null).block();
        assertFalse((Boolean) response.get("authenticated"));
    }

    @Test
    void csrfEndpointExposesTheHeaderNameAndToken() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-value");
        exchange.getAttributes().put(CsrfToken.class.getName(), Mono.just(token));

        var response = controller.csrf(exchange).block();

        assertEquals("X-XSRF-TOKEN", response.get("headerName"));
        assertEquals("csrf-value", response.get("token"));
    }
}
