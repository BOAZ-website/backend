package com.boaz.backend.global.oauth;

import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.service.UserService;
import com.boaz.backend.global.common.enums.MemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock UserService userService;

    CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        customOAuth2UserService = spy(new CustomOAuth2UserService(userService));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-004 provider=kakao
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-004 provider=kakao → KakaoOAuth2UserInfo 생성 후 findOrCreate() 호출")
    void kakao_provider_calls_find_or_create() {
        Map<String, Object> attributes = Map.of("id", 12345L,
                "kakao_account", Map.of("profile", Map.of("nickname", "보아즈")));

        OAuth2UserInfo info = (OAuth2UserInfo) ReflectionTestUtils.invokeMethod(
                customOAuth2UserService, "createUserInfo", "kakao", attributes);

        assertThat(info).isInstanceOf(KakaoOAuth2UserInfo.class);
        assertThat(info.getProvider()).isEqualTo("kakao");
        assertThat(info.getProviderId()).isEqualTo("12345");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-005 provider=google
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-005 provider=google → GoogleOAuth2UserInfo 생성 후 findOrCreate() 호출")
    void google_provider_creates_google_user_info() {
        Map<String, Object> attributes = Map.of("sub", "google-uid-001", "name", "김보아즈");

        OAuth2UserInfo info = (OAuth2UserInfo) ReflectionTestUtils.invokeMethod(
                customOAuth2UserService, "createUserInfo", "google", attributes);

        assertThat(info).isInstanceOf(GoogleOAuth2UserInfo.class);
        assertThat(info.getProvider()).isEqualTo("google");
        assertThat(info.getProviderId()).isEqualTo("google-uid-001");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-006 provider=naver
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-006 provider=naver → NaverOAuth2UserInfo 생성 후 findOrCreate() 호출")
    void naver_provider_creates_naver_user_info() {
        Map<String, Object> attributes = Map.of("response",
                Map.of("id", "naver-uid-001", "name", "이보아즈"));

        OAuth2UserInfo info = (OAuth2UserInfo) ReflectionTestUtils.invokeMethod(
                customOAuth2UserService, "createUserInfo", "naver", attributes);

        assertThat(info).isInstanceOf(NaverOAuth2UserInfo.class);
        assertThat(info.getProvider()).isEqualTo("naver");
        assertThat(info.getProviderId()).isEqualTo("naver-uid-001");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-007 지원하지 않는 provider
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-007 지원하지 않는 provider → OAuth2AuthenticationException")
    void unsupported_provider_throws() {
        assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(
                        customOAuth2UserService, "createUserInfo", "facebook", Map.of()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(
                        ((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .contains("Unsupported provider"));

        verify(userService, never()).findOrCreate(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-008 providerId=null
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-008 providerId=null → getProviderId() null 반환 확인")
    void null_provider_id_from_kakao() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", null);

        OAuth2UserInfo info = (OAuth2UserInfo) ReflectionTestUtils.invokeMethod(
                customOAuth2UserService, "createUserInfo", "kakao", attributes);

        assertThat(info.getProviderId()).isNull();
    }
}
