package com.boaz.backend.global.oauth;

import com.boaz.backend.domain.auth.entity.RefreshToken;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.enums.AccountType;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.security.JwtProvider;
import com.boaz.backend.global.util.CookieProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock JwtProvider jwtProvider;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock CookieProvider cookieProvider;
    @Mock Authentication authentication;

    OAuth2AuthenticationSuccessHandler handler;

    private static final String DEFAULT_REDIRECT = "http://localhost:3000/auth/callback";
    private static final String ALLOWED_REDIRECT = "http://localhost:3000/auth/callback";

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationSuccessHandler(jwtProvider, refreshTokenRepository, cookieProvider);
        ReflectionTestUtils.setField(handler, "defaultRedirectUri", DEFAULT_REDIRECT);
        ReflectionTestUtils.setField(handler, "allowedRedirectUris", List.of(ALLOWED_REDIRECT));
    }

    private User buildUser(Long id) {
        User user = User.builder()
                .provider("kakao").providerId("12345").nickname("보아즈")
                .memberType(MemberType.OUTSIDER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private OAuth2UserAdapter buildAdapter(User user) {
        org.springframework.security.oauth2.core.user.OAuth2User delegate =
                mock(org.springframework.security.oauth2.core.user.OAuth2User.class);
        return new OAuth2UserAdapter(delegate, user);
    }

    private String encodeRedirectUri(String uri) {
        return Base64.getUrlEncoder().encodeToString(uri.getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequest requestWithRedirectCookie(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("oauth2_redirect_uri", encodeRedirectUri(uri)));
        return request;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-019 최초 로그인 - RefreshToken 신규 저장
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-019 최초 로그인 → RefreshToken 신규 저장 + AccessToken 발급 + 쿠키 세팅")
    void first_login_saves_refresh_token() throws Exception {
        User user = buildUser(1L);
        when(authentication.getPrincipal()).thenReturn(buildAdapter(user));
        when(jwtProvider.generateUserAccessToken(1L)).thenReturn("access-token");
        when(jwtProvider.generateUserRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, 1L))
                .thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verify(refreshTokenRepository).save(argThat(rt ->
                rt.getAccountType() == AccountType.USER &&
                rt.getAccountId().equals(1L) &&
                rt.getToken().equals("refresh-token")));
        verify(cookieProvider).addUserRefreshTokenCookie(any(), eq("refresh-token"));
        assertThat(response.getRedirectedUrl()).contains("#token=access-token");
        assertThat(response.getRedirectedUrl()).startsWith(DEFAULT_REDIRECT);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-020 재로그인 - RefreshToken 업데이트
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-020 재로그인 → 기존 RefreshToken 업데이트 (save 없이 더티체킹)")
    void re_login_updates_refresh_token() throws Exception {
        User user = buildUser(1L);
        when(authentication.getPrincipal()).thenReturn(buildAdapter(user));
        when(jwtProvider.generateUserAccessToken(1L)).thenReturn("access-token");
        when(jwtProvider.generateUserRefreshToken(1L)).thenReturn("new-refresh-token");
        when(jwtProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(7));

        RefreshToken existingToken = spy(RefreshToken.builder()
                .accountType(AccountType.USER).accountId(1L)
                .token("old-refresh-token")
                .expiresAt(LocalDateTime.now().plusDays(7)).build());
        when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, 1L))
                .thenReturn(Optional.of(existingToken));

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verify(refreshTokenRepository, never()).save(any());
        verify(existingToken).update(eq("new-refresh-token"), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-021 허용된 redirect URI 쿠키 있는 경우
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-021 허용된 redirect URI 쿠키 → 해당 URI로 리다이렉트")
    void allowed_redirect_uri_is_used() throws Exception {
        User user = buildUser(1L);
        when(authentication.getPrincipal()).thenReturn(buildAdapter(user));
        when(jwtProvider.generateUserAccessToken(1L)).thenReturn("access-token");
        when(jwtProvider.generateUserRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, 1L))
                .thenReturn(Optional.empty());

        MockHttpServletRequest request = requestWithRedirectCookie(ALLOWED_REDIRECT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo(ALLOWED_REDIRECT + "#token=access-token");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-022 redirect URI 쿠키 없는 경우
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-022 redirect URI 쿠키 없음 → defaultRedirectUri로 리다이렉트")
    void no_cookie_uses_default_redirect() throws Exception {
        User user = buildUser(1L);
        when(authentication.getPrincipal()).thenReturn(buildAdapter(user));
        when(jwtProvider.generateUserAccessToken(1L)).thenReturn("access-token");
        when(jwtProvider.generateUserRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, 1L))
                .thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo(DEFAULT_REDIRECT + "#token=access-token");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-023 비허용 redirect URI
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-023 허용되지 않은 redirect URI → defaultRedirectUri로 리다이렉트")
    void disallowed_redirect_uri_uses_default() throws Exception {
        User user = buildUser(1L);
        when(authentication.getPrincipal()).thenReturn(buildAdapter(user));
        when(jwtProvider.generateUserAccessToken(1L)).thenReturn("access-token");
        when(jwtProvider.generateUserRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, 1L))
                .thenReturn(Optional.empty());

        MockHttpServletRequest request = requestWithRedirectCookie("http://evil.com/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo(DEFAULT_REDIRECT + "#token=access-token");
    }
}
