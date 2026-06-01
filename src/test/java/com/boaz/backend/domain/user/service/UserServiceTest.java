package com.boaz.backend.domain.user.service;

import com.boaz.backend.domain.user.dto.response.UserInfoResponse;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
}
