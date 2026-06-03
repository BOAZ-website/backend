package com.boaz.backend.domain.user.service;

import com.boaz.backend.domain.user.dto.response.UserInfoResponse;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.oauth.OAuth2UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    UserService userService;

    @Mock UserRepository userRepository;

    private User createUser(Long id, String nickname) {
        User u = User.builder()
                .provider("kakao").providerId("test-" + id)
                .nickname(nickname).memberType(MemberType.OUTSIDER)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("TC-001 유효한 userId 로 조회 → nickname 반환")
    void getMyInfo() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createUser(1L, "홍길동")));

        UserInfoResponse res = userService.getMyInfo(1L);

        assertThat(res.getNickname()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("TC-002 존재하지 않는 userId → USER_NOT_FOUND")
    void getMyInfoNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyInfo(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-004 findOrCreate
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-004 findOrCreate")
    class FindOrCreate {

        private OAuth2UserInfo mockUserInfo(String provider, String providerId, String nickname) {
            OAuth2UserInfo info = mock(OAuth2UserInfo.class);
            when(info.getProvider()).thenReturn(provider);
            when(info.getProviderId()).thenReturn(providerId);
            lenient().when(info.getNickname()).thenReturn(nickname);
            return info;
        }

        @Test
        @DisplayName("TC-001 최초 로그인 → User OUTSIDER로 신규 저장 후 반환")
        void first_login_creates_user() {
            OAuth2UserInfo userInfo = mockUserInfo("kakao", "12345", "보아즈");
            when(userRepository.findByProviderAndProviderId("kakao", "12345"))
                    .thenReturn(Optional.empty());
            User saved = User.builder().provider("kakao").providerId("12345")
                    .nickname("보아즈").memberType(MemberType.OUTSIDER).build();
            when(userRepository.save(any())).thenReturn(saved);

            User result = userService.findOrCreate(userInfo);

            verify(userRepository).findByProviderAndProviderId("kakao", "12345");
            verify(userRepository).save(argThat(u ->
                    u.getProvider().equals("kakao") &&
                    u.getProviderId().equals("12345") &&
                    u.getMemberType() == MemberType.OUTSIDER));
            assertThat(result.getMemberType()).isEqualTo(MemberType.OUTSIDER);
        }

        @Test
        @DisplayName("TC-002 재로그인 → 기존 User 반환, save 미호출")
        void re_login_returns_existing_user() {
            User existingUser = createUser(1L, "보아즈");
            OAuth2UserInfo userInfo = mockUserInfo("kakao", "12345", null);
            when(userRepository.findByProviderAndProviderId("kakao", "12345"))
                    .thenReturn(Optional.of(existingUser));

            User result = userService.findOrCreate(userInfo);

            verify(userRepository).findByProviderAndProviderId("kakao", "12345");
            verify(userRepository, never()).save(any());
            assertThat(result).isEqualTo(existingUser);
        }

        @Test
        @DisplayName("TC-003 같은 providerId라도 provider가 다르면 별도 User 생성")
        void different_provider_creates_new_user() {
            OAuth2UserInfo userInfo = mockUserInfo("google", "12345", "보아즈");
            when(userRepository.findByProviderAndProviderId("google", "12345"))
                    .thenReturn(Optional.empty());
            User saved = User.builder().provider("google").providerId("12345")
                    .nickname("보아즈").memberType(MemberType.OUTSIDER).build();
            when(userRepository.save(any())).thenReturn(saved);

            userService.findOrCreate(userInfo);

            verify(userRepository).findByProviderAndProviderId("google", "12345");
            verify(userRepository).save(argThat(u -> u.getProvider().equals("google")));
        }
    }
}
