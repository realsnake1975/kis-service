package dev.kis.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class SessionController {
    @GetMapping("/api/session")
    Mono<Map<String, Object>> session(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) return Mono.just(Map.of("authenticated", false));
        Object displayName = user.getAttribute("name");
        if (displayName == null) displayName = user.getAttribute("login");
        if (displayName == null) displayName = "Investor";
        return Mono.just(Map.of("authenticated", true,
                "name", displayName,
                "email", user.getAttribute("email") == null ? "" : user.getAttribute("email")));
    }

    @GetMapping("/api/session/csrf")
    Mono<Map<String, String>> csrf(ServerWebExchange exchange) {
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        return token.map(value -> Map.of("headerName", value.getHeaderName(), "token", value.getToken()));
    }
}
