package com.boaz.backend.domain.user.integration;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.domain.user.dto.response.PromoteUsersResponse;
import com.boaz.backend.domain.user.dto.response.UserInfoResponse;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.domain.user.service.UserAdminService;
import com.boaz.backend.domain.user.service.UserService;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.support.TestcontainersBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class UserIntegrationTest extends TestcontainersBase {

    @Autowired UserService userService;
    @Autowired UserAdminService userAdminService;
    @Autowired UserRepository userRepository;
    @Autowired RecruitmentRepository recruitmentRepository;
    @Autowired ApplicantRepository applicantRepository;

    @PersistenceContext EntityManager em;

    private int seq = 0;

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
                .provider("kakao").providerId("p" + (++seq)).nickname(nickname)
                .memberType(MemberType.OUTSIDER).build());
    }

    private Applicant saveSubmittedApplicant(User u, Recruitment r) {
        return applicantRepository.save(Applicant.builder()
                .recruitment(r).user(u).status(Applicant.ApplicantStatus.SUBMITTED)
                .track(Track.ENGINEERING).name("홍길동").email("hong@example.com")
                .phone("01012345678").university("한국대").major("컴공")
                .build());
    }

    @Nested
    @DisplayName("내 정보 조회 end-to-end (USER-001)")
    class GetMyInfo {

        @Test
        @DisplayName("저장된 유저 → nickname 반환")
        void found() {
            User u = saveUser("홍길동");
            em.flush();
            em.clear();

            UserInfoResponse res = userService.getMyInfo(u.getId());

            assertThat(res.getNickname()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("존재하지 않는 userId → USER_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> userService.getMyInfo(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("합격자 승격 end-to-end (USER-ADMIN-001)")
    class BulkPromote {

        @Test
        @DisplayName("SUBMITTED 지원서 보유 → MEMBER 전환 + 개인정보가 실제 DB에 영속")
        void promotePersists() {
            User u = saveUser("닉네임");
            Recruitment r = recruitmentRepository.save(Recruitment.create(27,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), "[]", null));
            saveSubmittedApplicant(u, r);
            em.flush();
            em.clear();

            PromoteUsersResponse res = userAdminService.bulkPromote(List.of(u.getId()));
            em.flush();
            em.clear();

            assertThat(res.getFailedUserIds()).isEmpty();
            User promoted = userRepository.findById(u.getId()).orElseThrow();
            assertThat(promoted.getMemberType()).isEqualTo(MemberType.MEMBER);
            assertThat(promoted.getName()).isEqualTo("홍길동");
            assertThat(promoted.getPhone()).isEqualTo("01012345678");
            assertThat(promoted.getUniversity()).isEqualTo("한국대");
            assertThat(promoted.getMajor()).isEqualTo("컴공");
            assertThat(promoted.getTrack()).isEqualTo(Track.ENGINEERING);
            assertThat(promoted.getTerm()).isEqualTo(27);
        }

        @Test
        @DisplayName("SUBMITTED 지원서 없음 → APPLICATION_NOT_FOUND, 승격 안 됨")
        void noApplication() {
            User u = saveUser("닉네임");
            em.flush();
            em.clear();

            PromoteUsersResponse res = userAdminService.bulkPromote(List.of(u.getId()));
            em.flush();
            em.clear();

            assertThat(res.getFailedUserIds()).hasSize(1);
            assertThat(res.getFailedUserIds().get(0).getErrorCode()).isEqualTo("APPLICATION_NOT_FOUND");
            assertThat(userRepository.findById(u.getId()).orElseThrow().getMemberType())
                    .isEqualTo(MemberType.OUTSIDER);
        }
    }
}
