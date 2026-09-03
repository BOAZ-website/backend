package com.boaz.backend.global.oauth;

import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.client.RestOperations;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock UserService userService;
    @Mock RestOperations restOperations;

    CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        customOAuth2UserService = new CustomOAuth2UserService(userService);
        // super.loadUser() 의 userinfo 엔드포인트 HTTP 호출을 mock 으로 대체
        customOAuth2UserService.setRestOperations(restOperations);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────
    private OAuth2UserRequest userRequest(String registrationId, String userNameAttributeName) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://provider.example/authorize")
                .tokenUri("https://provider.example/token")
                .userInfoUri("https://provider.example/userinfo")
                .userNameAttributeName(userNameAttributeName)
                .clientName(registrationId)
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-token",
                Instant.now(), Instant.now().plusSeconds(3600));
        return new OAuth2UserRequest(registration, accessToken);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubUserInfo(Map<String, Object> attributes) {
        when(restOperations.exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(attributes, HttpStatus.OK));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-004 provider=kakao
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-004 provider=kakao → KakaoOAuth2UserInfo 파싱값으로 findOrCreate() 호출, OAuth2UserAdapter 반환")
    void kakao_provider_calls_find_or_create() {
        stubUserInfo(Map.of(
                "id", 12345L,
                "kakao_account", Map.of("profile", Map.of("nickname", "보아즈"))));
        when(userService.findOrCreate(any())).thenReturn(mock(User.class));

        var result = customOAuth2UserService.loadUser(userRequest("kakao", "id"));

        assertThat(result).isInstanceOf(OAuth2UserAdapter.class);
        verify(userService).findOrCreate(argThat(info ->
                info.getProvider().equals("kakao") && info.getProviderId().equals("12345")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-005 provider=google
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-005 provider=google → GoogleOAuth2UserInfo 파싱값으로 findOrCreate() 호출")
    void google_provider_calls_find_or_create() {
        stubUserInfo(Map.of("sub", "google-uid-001", "name", "김보아즈"));
        when(userService.findOrCreate(any())).thenReturn(mock(User.class));

        var result = customOAuth2UserService.loadUser(userRequest("google", "sub"));

        assertThat(result).isInstanceOf(OAuth2UserAdapter.class);
        verify(userService).findOrCreate(argThat(info ->
                info.getProvider().equals("google") && info.getProviderId().equals("google-uid-001")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-006 provider=naver
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-006 provider=naver → NaverOAuth2UserInfo 파싱값으로 findOrCreate() 호출")
    void naver_provider_calls_find_or_create() {
        stubUserInfo(Map.of("response", Map.of("id", "naver-uid-001", "name", "이보아즈")));
        when(userService.findOrCreate(any())).thenReturn(mock(User.class));

        var result = customOAuth2UserService.loadUser(userRequest("naver", "response"));

        assertThat(result).isInstanceOf(OAuth2UserAdapter.class);
        verify(userService).findOrCreate(argThat(info ->
                info.getProvider().equals("naver") && info.getProviderId().equals("naver-uid-001")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-007 지원하지 않는 provider
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-007 지원하지 않는 provider → OAuth2AuthenticationException, findOrCreate 미호출")
    void unsupported_provider_throws() {
        stubUserInfo(Map.of("id", 999L));
        OAuth2UserRequest request = userRequest("facebook", "id");

        assertThatThrownBy(() -> customOAuth2UserService.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .contains("Unsupported provider"));

        verify(userService, never()).findOrCreate(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-008 providerId=null
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-008 providerId=null → OAuth2AuthenticationException, findOrCreate 미호출")
    void null_provider_id_throws() {
        // DefaultOAuth2User 는 name attribute 값이 null 이면 거부하므로,
        // id 키 자체를 응답에서 제외해 KakaoOAuth2UserInfo.getProviderId() 가 null 이 되도록 한다
        stubUserInfo(Map.of("connected_at", "2026-01-01T00:00:00Z"));
        OAuth2UserRequest request = userRequest("kakao", "connected_at");

        assertThatThrownBy(() -> customOAuth2UserService.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .contains("Provider ID is missing"));

        verify(userService, never()).findOrCreate(any());
    }
}
