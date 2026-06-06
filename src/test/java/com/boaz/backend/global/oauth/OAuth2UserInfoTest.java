package com.boaz.backend.global.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2UserInfoTest {

    // ──────────────────────────────────────────────────────────────────────────
    // KakaoOAuth2UserInfo
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("KakaoOAuth2UserInfo")
    class KakaoTests {

        @Test
        @DisplayName("TC-009 id가 있는 경우 → getProviderId() Long→String 변환")
        void providerId_long_to_string() {
            KakaoOAuth2UserInfo info = new KakaoOAuth2UserInfo(Map.of("id", 12345L));
            assertThat(info.getProviderId()).isEqualTo("12345");
        }

        @Test
        @DisplayName("TC-010 kakao_account.profile.nickname 있는 경우 → getNickname() 반환")
        void nickname_from_nested_profile() {
            Map<String, Object> profile = Map.of("nickname", "보아즈");
            Map<String, Object> kakaoAccount = Map.of("profile", profile);
            KakaoOAuth2UserInfo info = new KakaoOAuth2UserInfo(
                    Map.of("id", 12345L, "kakao_account", kakaoAccount));

            assertThat(info.getNickname()).isEqualTo("보아즈");
        }

        @Test
        @DisplayName("TC-011 kakao_account 없는 경우 → getNickname() null (NPE 없음)")
        void nickname_null_when_no_kakao_account() {
            KakaoOAuth2UserInfo info = new KakaoOAuth2UserInfo(Map.of("id", 12345L));
            assertThat(info.getNickname()).isNull();
        }

        @Test
        @DisplayName("TC-012 profile 없는 경우 → getNickname() null (NPE 없음)")
        void nickname_null_when_no_profile() {
            Map<String, Object> kakaoAccount = new HashMap<>();
            KakaoOAuth2UserInfo info = new KakaoOAuth2UserInfo(
                    Map.of("id", 12345L, "kakao_account", kakaoAccount));

            assertThat(info.getNickname()).isNull();
        }

        @Test
        @DisplayName("id가 null인 경우 → getProviderId() null")
        void providerId_null_when_id_null() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("id", null);
            KakaoOAuth2UserInfo info = new KakaoOAuth2UserInfo(attrs);

            assertThat(info.getProviderId()).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GoogleOAuth2UserInfo
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GoogleOAuth2UserInfo")
    class GoogleTests {

        @Test
        @DisplayName("TC-013 sub 있는 경우 → getProviderId() 반환")
        void providerId_from_sub() {
            GoogleOAuth2UserInfo info = new GoogleOAuth2UserInfo(
                    Map.of("sub", "google-uid-001", "name", "김보아즈"));

            assertThat(info.getProviderId()).isEqualTo("google-uid-001");
        }

        @Test
        @DisplayName("TC-014 name 있는 경우 → getNickname() 반환")
        void nickname_from_name() {
            GoogleOAuth2UserInfo info = new GoogleOAuth2UserInfo(
                    Map.of("sub", "google-uid-001", "name", "김보아즈"));

            assertThat(info.getNickname()).isEqualTo("김보아즈");
        }

        @Test
        @DisplayName("getProvider() → \"google\"")
        void provider_is_google() {
            GoogleOAuth2UserInfo info = new GoogleOAuth2UserInfo(Map.of("sub", "id"));
            assertThat(info.getProvider()).isEqualTo("google");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // NaverOAuth2UserInfo
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("NaverOAuth2UserInfo")
    class NaverTests {

        @Test
        @DisplayName("TC-015 response.id 있는 경우 → getProviderId() 반환")
        void providerId_from_response_id() {
            NaverOAuth2UserInfo info = new NaverOAuth2UserInfo(
                    Map.of("response", Map.of("id", "naver-uid-001", "name", "이보아즈")));

            assertThat(info.getProviderId()).isEqualTo("naver-uid-001");
        }

        @Test
        @DisplayName("TC-016 name이 있는 경우 → getNickname() = name (nickname 무시)")
        void nickname_prefers_name() {
            NaverOAuth2UserInfo info = new NaverOAuth2UserInfo(Map.of("response", Map.of(
                    "id", "naver-uid-001", "name", "이보아즈", "nickname", "보아즈닉")));

            assertThat(info.getNickname()).isEqualTo("이보아즈");
        }

        @Test
        @DisplayName("TC-017 name 비어있고 nickname 있는 경우 → getNickname() = nickname 폴백")
        void nickname_fallback_when_name_blank() {
            NaverOAuth2UserInfo info = new NaverOAuth2UserInfo(Map.of("response", Map.of(
                    "id", "naver-uid-001", "name", "", "nickname", "보아즈닉")));

            assertThat(info.getNickname()).isEqualTo("보아즈닉");
        }

        @Test
        @DisplayName("TC-018 response 없는 경우 → getProviderId()/getNickname() null (NPE 없음)")
        void null_when_no_response() {
            NaverOAuth2UserInfo info = new NaverOAuth2UserInfo(Map.of());

            assertThat(info.getProviderId()).isNull();
            assertThat(info.getNickname()).isNull();
        }
    }
}
