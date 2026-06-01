package com.boaz.backend.domain.user.service;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.user.dto.response.PromoteUsersResponse;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @InjectMocks
    UserAdminService userAdminService;

    @Mock UserRepository userRepository;
    @Mock ApplicantRepository applicantRepository;
    @Mock TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        // TransactionTemplate.execute(callback) → 콜백을 실제로 실행하도록 스텁
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private User outsider(Long id) {
        User u = User.builder()
                .provider("kakao").providerId("p" + id)
                .nickname("nick").memberType(MemberType.OUTSIDER)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private User member(Long id) {
        User u = User.builder()
                .provider("kakao").providerId("p" + id)
                .nickname("nick").memberType(MemberType.MEMBER)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Applicant submittedApplicant(int term) {
        Recruitment r = Recruitment.create(term,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), "[]", null);
        return Applicant.builder()
                .recruitment(r)
                .status(Applicant.ApplicantStatus.SUBMITTED)
                .track(Track.ENGINEERING)
                .name("홍길동").email("hong@example.com").phone("01012345678")
                .university("한국대").major("컴공")
                .build();
    }

    @Test
    @DisplayName("TC-001 전체 성공 → failedUserIds 빈 배열")
    void allSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(outsider(1L)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(outsider(2L)));
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider(3L)));
        when(applicantRepository.findByUserIdAndStatus(anyLong(), eq(Applicant.ApplicantStatus.SUBMITTED)))
                .thenReturn(Optional.of(submittedApplicant(27)));

        PromoteUsersResponse res = userAdminService.bulkPromote(List.of(1L, 2L, 3L));

        assertThat(res.getFailedUserIds()).isEmpty();
    }

    @Test
    @DisplayName("TC-002 일부 실패(USER_NOT_FOUND) → skip 후 failedUserIds 포함")
    void partialFailUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(outsider(1L)));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(outsider(2L)));
        when(applicantRepository.findByUserIdAndStatus(anyLong(), eq(Applicant.ApplicantStatus.SUBMITTED)))
                .thenReturn(Optional.of(submittedApplicant(27)));

        PromoteUsersResponse res = userAdminService.bulkPromote(List.of(1L, 999L, 2L));

        assertThat(res.getFailedUserIds()).hasSize(1);
        assertThat(res.getFailedUserIds().get(0).getUserId()).isEqualTo(999L);
        assertThat(res.getFailedUserIds().get(0).getErrorCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-003 일부 실패(APPLICATION_NOT_FOUND) → skip")
    void partialFailApplicationNotFound() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(outsider(5L)));
        when(applicantRepository.findByUserIdAndStatus(5L, Applicant.ApplicantStatus.SUBMITTED))
                .thenReturn(Optional.empty());

        PromoteUsersResponse res = userAdminService.bulkPromote(List.of(5L));

        assertThat(res.getFailedUserIds()).hasSize(1);
        assertThat(res.getFailedUserIds().get(0).getUserId()).isEqualTo(5L);
        assertThat(res.getFailedUserIds().get(0).getErrorCode()).isEqualTo("APPLICATION_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-004 이미 MEMBER → skip + ALREADY_MEMBER")
    void alreadyMember() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(member(10L)));

        PromoteUsersResponse res = userAdminService.bulkPromote(List.of(10L));

        assertThat(res.getFailedUserIds()).hasSize(1);
        assertThat(res.getFailedUserIds().get(0).getUserId()).isEqualTo(10L);
        assertThat(res.getFailedUserIds().get(0).getErrorCode()).isEqualTo("ALREADY_MEMBER");
        // 이미 MEMBER 면 지원서 조회 단계에 도달하지 않음
        verify(applicantRepository, never()).findByUserIdAndStatus(anyLong(), any());
    }

    @Test
    @DisplayName("TC-005 승격 시 user 필드 복사 검증")
    void promoteCopiesFields() {
        User user = outsider(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(applicantRepository.findByUserIdAndStatus(1L, Applicant.ApplicantStatus.SUBMITTED))
                .thenReturn(Optional.of(submittedApplicant(27)));

        userAdminService.bulkPromote(List.of(1L));

        assertThat(user.getMemberType()).isEqualTo(MemberType.MEMBER);
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getPhone()).isEqualTo("01012345678");
        assertThat(user.getUniversity()).isEqualTo("한국대");
        assertThat(user.getMajor()).isEqualTo("컴공");
        assertThat(user.getTrack()).isEqualTo(Track.ENGINEERING);
        assertThat(user.getTerm()).isEqualTo(27);
    }

    @Test
    @DisplayName("TC-006 여러 명 일부 실패 혼합 → 성공한 건만 처리")
    void mixedResult() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(outsider(1L)));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());          // USER_NOT_FOUND
        when(userRepository.findById(3L)).thenReturn(Optional.of(member(3L)));   // ALREADY_MEMBER
        when(applicantRepository.findByUserIdAndStatus(1L, Applicant.ApplicantStatus.SUBMITTED))
                .thenReturn(Optional.of(submittedApplicant(27)));

        PromoteUsersResponse res = userAdminService.bulkPromote(List.of(1L, 2L, 3L));

        assertThat(res.getFailedUserIds()).hasSize(2);
        assertThat(res.getFailedUserIds()).extracting(PromoteUsersResponse.FailedUserInfo::getUserId)
                .containsExactly(2L, 3L);
        assertThat(res.getFailedUserIds()).extracting(PromoteUsersResponse.FailedUserInfo::getErrorCode)
                .containsExactly("USER_NOT_FOUND", "ALREADY_MEMBER");
    }

    @Test
    @DisplayName("TC-007 DB/서버 오류(RuntimeException) → 즉시 중단, 이후 userId 미처리")
    void runtimeExceptionAborts() {
        when(userRepository.findById(1L)).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> userAdminService.bulkPromote(List.of(1L, 2L)))
                .isInstanceOf(RuntimeException.class);

        verify(userRepository, never()).findById(2L);
    }
}
