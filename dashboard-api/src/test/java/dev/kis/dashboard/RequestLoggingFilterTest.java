package dev.kis.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLoggingFilterTest {
    private final List<String> lines = new ArrayList<>();
    private final RequestLoggingFilter filter = new RequestLoggingFilter(lines::add);

    private MockServerWebExchange exchange(MockServerHttpRequest request, Principal principal) {
        var builder = MockServerWebExchange.builder(request);
        if (principal != null) builder.principal(principal);
        return builder.build();
    }

    /** Runs the outer filter, then UserCapture, then a handler that sets the status. */
    private void run(MockServerWebExchange exchange, HttpStatus status) {
        WebFilterChain handler = ex -> {
            ex.getResponse().setStatusCode(status);
            return Mono.empty();
        };
        WebFilterChain afterSecurity = ex -> new RequestLoggingFilter.UserCapture().filter(ex, handler);
        filter.filter(exchange, afterSecurity).block();
    }

    @Test
    void logsMethodPathStatusDurationAndUser() {
        run(exchange(MockServerHttpRequest.get("/api/account/balance").build(),
                new TestingAuthenticationToken("github:8158999", null, "ROLE_USER")), HttpStatus.OK);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches("GET /api/account/balance 200 \\d+ms user=github:8158999"), lines.get(0));
    }

    @Test
    void neverLogsTheQueryStringSoOAuthCodesStayOutOfLogs() {
        run(exchange(MockServerHttpRequest.get("/login/oauth2/code/github?code=one-time-code&state=xyz").build(), null),
                HttpStatus.FOUND);

        assertTrue(lines.get(0).startsWith("GET /login/oauth2/code/github 302 "), lines.get(0));
        assertTrue(!lines.get(0).contains("one-time-code") && !lines.get(0).contains("state="), lines.get(0));
        assertTrue(lines.get(0).endsWith("user=-"));
    }

    @Test
    void anonymousUsersAreLoggedAsDash() {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        run(exchange(MockServerHttpRequest.get("/api/session").build(), anonymous), HttpStatus.OK);
        assertTrue(lines.get(0).endsWith("user=-"), lines.get(0));
    }

    @Test
    void requestsRejectedBeforeReachingTheAppAreStillLogged() {
        // Security answers 403 itself and never calls the rest of the chain (so UserCapture does not run).
        var exchange = exchange(MockServerHttpRequest.post("/logout").build(), null);
        filter.filter(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return Mono.empty();
        }).block();
        assertTrue(lines.get(0).startsWith("POST /logout 403 "), lines.get(0));
    }

    @Test
    void skipsStaticAssets() {
        run(exchange(MockServerHttpRequest.get("/assets/index-abc.js").build(), null), HttpStatus.OK);
        assertTrue(lines.isEmpty());
    }

    @Test
    void cancelledRequestsAreMarked() {
        var exchange = exchange(MockServerHttpRequest.get("/api/schedule").build(), null);
        filter.filter(exchange, ex -> Mono.never()).subscribe().dispose();
        assertTrue(lines.get(0).startsWith("GET /api/schedule cancelled "), lines.get(0));
    }
}
