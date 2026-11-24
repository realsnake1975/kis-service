package dev.kis.dashboard;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/** Stable key for a login user: OAuth provider plus the provider's user id, e.g. {@code github:123456}. */
final class UserKeys {
    private UserKeys() {
    }

    static String of(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (authentication instanceof OAuth2AuthenticationToken oauth) {
            return oauth.getAuthorizedClientRegistrationId() + ":" + oauth.getName();
        }
        return authentication.getName();
    }
}
