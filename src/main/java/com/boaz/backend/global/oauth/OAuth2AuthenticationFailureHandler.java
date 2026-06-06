package com.boaz.backend.global.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${oauth2.failure-redirect-uri}")
    private String defaultFailureRedirectUri;

    @Value("${oauth2.allowed-redirect-uris}")
    private List<String> allowedRedirectUris;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String targetUri = CookieOAuth2AuthorizationRequestRepository
                .resolveRedirectUriFromCookie(request)
                .filter(allowedRedirectUris::contains)
                .flatMap(uri -> {
                    try {
                        URI parsed = URI.create(uri);
                        return Optional.of(parsed.getScheme() + "://" + parsed.getAuthority() + "/login?error=true");
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                })
                .orElse(defaultFailureRedirectUri);

        getRedirectStrategy().sendRedirect(request, response, targetUri);
    }
}
