package com.boaz.backend.global.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuth2AuthenticationFailureHandlerTest {

    OAuth2AuthenticationFailureHandler handler;

    private static final String DEFAULT_FAILURE_URI = "http://localhost:3000/login?error=true";
    private static final String ALLOWED_REDIRECT = "http://localhost:3000/auth/callback";

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationFailureHandler();
        ReflectionTestUtils.setField(handler, "defaultFailureRedirectUri", DEFAULT_FAILURE_URI);
        ReflectionTestUtils.setField(handler, "allowedRedirectUris", List.of(ALLOWED_REDIRECT));
    }

    private String encodeRedirectUri(String uri) {
        return Base64.getUrlEncoder().encodeToString(uri.getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequest requestWithRedirectCookie(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("oauth2_redirect_uri", encodeRedirectUri(uri)));
        return request;
    }

    private AuthenticationException authException() {
        return mock(AuthenticationException.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-024 허용된 redirect URI 쿠키 있는 경우
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-024 허용된 redirect URI 쿠키 → {scheme}://{authority}/login?error=true 리다이렉트")
    void allowed_redirect_uri_redirects_to_login_error() throws Exception {
        MockHttpServletRequest request = requestWithRedirectCookie(ALLOWED_REDIRECT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, authException());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/login?error=true");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-025 redirect URI 쿠키 없는 경우
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-025 redirect URI 쿠키 없음 → defaultFailureRedirectUri로 리다이렉트")
    void no_cookie_uses_default_failure_redirect() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, authException());

        assertThat(response.getRedirectedUrl()).isEqualTo(DEFAULT_FAILURE_URI);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-026 비허용 redirect URI
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-026 허용되지 않은 redirect URI → defaultFailureRedirectUri로 리다이렉트")
    void disallowed_redirect_uri_uses_default_failure() throws Exception {
        MockHttpServletRequest request = requestWithRedirectCookie("http://evil.com/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, authException());

        assertThat(response.getRedirectedUrl()).isEqualTo(DEFAULT_FAILURE_URI);
    }
}
