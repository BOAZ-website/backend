package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.config.JpaConfig;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class ApplicantRepositoryTest extends TestcontainersBase {

    @Autowired ApplicantRepository applicantRepository;
    @Autowired TestEntityManager em;

    private Recruitment persistRecruitment(int term) {
        LocalDateTime now = LocalDateTime.now();
        Recruitment r = Recruitment.create(term, now.minusDays(1), now.plusDays(1), "{}", null);
        return em.persistFlushFind(r);
    }

    private User persistUser(String providerId) {
        User u = User.builder()
                .provider("kakao").providerId(providerId)
                .nickname("nick-" + providerId).memberType(MemberType.OUTSIDER)
                .build();
        return em.persistFlushFind(u);
    }

    private Applicant persistApplicant(Recruitment r, User u, Track track,
                                       Applicant.ApplicantStatus status, LocalDateTime submittedAt) {
        Applicant a = Applicant.builder()
                .recruitment(r).user(u).status(status).track(track)
                .name("name").email("a@example.com").phone("01012345678")
                .build();
        if (submittedAt != null) {
            ReflectionTestUtils.setField(a, "submittedAt", submittedAt);
        }
        return em.persistFlushFind(a);
    }

    @Nested
    @DisplayName("findByRecruitmentIdAndUserId")
    class FindByRecruitmentIdAndUserId {

        @Test
        @DisplayName("(recruitmentId, userId) 조합 일치 시 반환, 불일치 시 empty")
        void match() {
            Recruitment r = persistRecruitment(27);
            User u = persistUser("p1");
            persistApplicant(r, u, Track.ENGINEERING, Applicant.ApplicantStatus.DRAFT, null);

            assertThat(applicantRepository.findByRecruitmentIdAndUserId(r.getId(), u.getId())).isPresent();
            assertThat(applicantRepository.findByRecruitmentIdAndUserId(r.getId(), 999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findSubmittedByRecruitmentIdAndTrackAndDecision")
    class FindSubmittedByTrack {

        @Test
        @DisplayName("decision=null → 지정 트랙 SUBMITTED 전체, submitted_at 오름차순, user JOIN FETCH")
        void submittedOrdered() {
            Recruitment r = persistRecruitment(27);
            LocalDateTime base = LocalDateTime.now();

            // ENGINEERING SUBMITTED 2건 (삽입 순서와 submittedAt 순서를 반대로)
            persistApplicant(r, persistUser("eng-late"), Track.ENGINEERING,
                    Applicant.ApplicantStatus.SUBMITTED, base.plusHours(2));
            persistApplicant(r, persistUser("eng-early"), Track.ENGINEERING,
                    Applicant.ApplicantStatus.SUBMITTED, base.plusHours(1));
            // 다른 트랙 / DRAFT 는 제외돼야 함
            persistApplicant(r, persistUser("ana"), Track.ANALYSIS,
                    Applicant.ApplicantStatus.SUBMITTED, base.plusHours(1));
            persistApplicant(r, persistUser("eng-draft"), Track.ENGINEERING,
                    Applicant.ApplicantStatus.DRAFT, null);
            em.clear();

            List<Applicant> result = applicantRepository
                    .findSubmittedByRecruitmentIdAndTrackAndDecision(
                            r.getId(), Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED, null);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSubmittedAt()).isBefore(result.get(1).getSubmittedAt());
            // JOIN FETCH 로 user 가 즉시 로딩되어 접근 가능해야 함
            assertThat(result.get(0).getUser().getId()).isNotNull();
        }

        @Test
        @DisplayName("decision=PASS → 해당 트랙의 final_decision=PASS 지원자만 반환")
        void filterByPassDecision() {
            Recruitment r = persistRecruitment(27);
            LocalDateTime base = LocalDateTime.now();

            Applicant pass = persistApplicant(r, persistUser("eng-pass"), Track.ENGINEERING,
                    Applicant.ApplicantStatus.SUBMITTED, base.plusHours(1));
            pass.updateFinalDecision(EvaluationDecision.PASS);
            Applicant fail = persistApplicant(r, persistUser("eng-fail"), Track.ENGINEERING,
                    Applicant.ApplicantStatus.SUBMITTED, base.plusHours(2));
            fail.updateFinalDecision(EvaluationDecision.FAIL);
            em.flush();
            em.clear();

            List<Applicant> passResult = applicantRepository
                    .findSubmittedByRecruitmentIdAndTrackAndDecision(
                            r.getId(), Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED,
                            EvaluationDecision.PASS);
            List<Applicant> failResult = applicantRepository
                    .findSubmittedByRecruitmentIdAndTrackAndDecision(
                            r.getId(), Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED,
                            EvaluationDecision.FAIL);

            assertThat(passResult).hasSize(1);
            assertThat(passResult.get(0).getFinalDecision()).isEqualTo(EvaluationDecision.PASS);
            assertThat(failResult).hasSize(1);
            assertThat(failResult.get(0).getFinalDecision()).isEqualTo(EvaluationDecision.FAIL);
        }
    }

    @Nested
    @DisplayName("existsByRecruitmentId / deleteByRecruitmentId")
    class ExistsAndDelete {

        @Test
        @DisplayName("공고 연관 지원자 존재 여부 및 공고 단위 삭제")
        void existsAndDelete() {
            Recruitment r = persistRecruitment(27);
            persistApplicant(r, persistUser("p1"), Track.ENGINEERING, Applicant.ApplicantStatus.DRAFT, null);

            assertThat(applicantRepository.existsByRecruitmentId(r.getId())).isTrue();

            applicantRepository.deleteByRecruitmentId(r.getId());
            em.flush();
            em.clear();

            assertThat(applicantRepository.existsByRecruitmentId(r.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndStatus")
    class FindByUserIdAndStatus {

        @Test
        @DisplayName("userId + status 로 조회 (recruitment JOIN FETCH)")
        void byUserAndStatus() {
            Recruitment r = persistRecruitment(27);
            User u = persistUser("p1");
            persistApplicant(r, u, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED, LocalDateTime.now());
            em.clear();

            Optional<Applicant> found = applicantRepository.findByUserIdAndStatus(
                    u.getId(), Applicant.ApplicantStatus.SUBMITTED);

            assertThat(found).isPresent();
            assertThat(found.get().getRecruitment().getTerm()).isEqualTo(27);
            assertThat(applicantRepository.findByUserIdAndStatus(
                    u.getId(), Applicant.ApplicantStatus.DRAFT)).isEmpty();
        }
    }
}
