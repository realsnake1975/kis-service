package dev.kis.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.security.Principal;
import java.util.function.Consumer;

/**
 * One access-log line per request: {@code GET /api/account/balance 200 142ms user=github:123}.
 * <p>
 * Only the path is logged, never the query string: OAuth callbacks carry one-time {@code code}/{@code state}
 * values there. Static assets are skipped. The outer filter runs before Spring Security, so requests that security
 * redirects or rejects are logged too; {@link UserCapture} runs after it and records who the user is.
 */
@Component
public class RequestLoggingFilter implements WebFilter, Ordered {
    static final String USER_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".user";
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("dev.kis.dashboard.access");

    private final Consumer<String> sink;

    public RequestLoggingFilter() {
        this(ACCESS_LOG::info);
    }

    RequestLoggingFilter(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isStatic(path)) return chain.filter(exchange);
        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> sink.accept(format(exchange, path, signal, start)));
    }

    static boolean isStatic(String path) {
        return path.startsWith("/assets/") || path.equals("/favicon.ico") || path.equals("/") || path.equals("/index.html");
    }

    private static String format(ServerWebExchange exchange, String path, SignalType signal, long start) {
        long millis = (System.nanoTime() - start) / 1_000_000;
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        String outcome = signal == SignalType.CANCEL ? "cancelled" : status == null ? "200" : Integer.toString(status.value());
        String user = exchange.getAttributeOrDefault(USER_ATTRIBUTE, "-");
        return exchange.getRequest().getMethod().name() + " " + path + " " + outcome + " " + millis + "ms user=" + user;
    }

    static String userKey(Principal principal) {
        if (principal instanceof OAuth2AuthenticationToken oauth) {
            return oauth.getAuthorizedClientRegistrationId() + ":" + oauth.getName();
        }
        return principal.getName();
    }

    /** Runs after Spring Security and stores the signed-in user's key for the access-log line. */
    @Component
    public static class UserCapture implements WebFilter, Ordered {
        @Override
        public int getOrder() {
            return 0; // after Spring Security's WebFilterChainProxy (-100)
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            return exchange.getPrincipal()
                    .filter(principal -> !(principal instanceof AnonymousAuthenticationToken))
                    .doOnNext(principal -> exchange.getAttributes().put(USER_ATTRIBUTE, userKey(principal)))
                    .then(chain.filter(exchange));
        }
    }
}
