package com.boaz.backend.domain.recruitment.integration;

import com.boaz.backend.domain.recruitment.dto.request.ApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.SubscriptionRequest;
import com.boaz.backend.domain.recruitment.dto.response.ApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentStatusResponse;
import com.boaz.backend.domain.recruitment.dto.response.SubscriptionResponse;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion.Category;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion.Type;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.repository.ApplicantAnswerRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicationQuestionRepository;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.domain.recruitment.repository.SubscriptionRepository;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.util.S3Service;
import com.boaz.backend.support.TestcontainersBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class RecruitmentIntegrationTest extends TestcontainersBase {

    @Autowired RecruitmentService recruitmentService;
    @Autowired RecruitmentRepository recruitmentRepository;
    @Autowired ApplicationQuestionRepository questionRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired ApplicantAnswerRepository answerRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper objectMapper;

    @PersistenceContext EntityManager em;

    // S3 는 외부 연동이므로 통합 테스트에서 모킹
    @MockBean S3Service s3Service;

    private int userSeq = 0;

    private Recruitment saveActiveRecruitment(int term) {
        LocalDateTime now = LocalDateTime.now();
        return recruitmentRepository.save(
                Recruitment.create(term, now.minusDays(1), now.plusDays(1), "{}", null));
    }

    private Recruitment saveClosedRecruitment(int term) {
        LocalDateTime now = LocalDateTime.now();
        return recruitmentRepository.save(
                Recruitment.create(term, now.minusDays(10), now.minusDays(1), "{}", null));
    }

    private User saveUser() {
        return userRepository.save(User.builder()
                .provider("kakao").providerId("p" + (++userSeq))
                .nickname("nick").memberType(MemberType.OUTSIDER)
                .build());
    }

    private ApplicationQuestion saveQuestion(Recruitment r, Category category, int orderNum) {
        return questionRepository.save(ApplicationQuestion.create(
                r, "label-" + category + "-" + orderNum, category, Type.TEXT,
                "content", 500, null, orderNum, true));
    }

    private ApplicationRequest buildSubmitRequest(Track track, Long commonQId, Long trackQId) {
        Map<String, Object> req = Map.ofEntries(
                Map.entry("track", track.name()),
                Map.entry("name", "홍길동"),
                Map.entry("email", "hong@example.com"),
                Map.entry("phone", "01012345678"),
                Map.entry("university", "한국대학교"),
                Map.entry("major", "컴퓨터공학"),
                Map.entry("last_semester", 4),
                Map.entry("military_status", "COMPLETED_OR_EXEMPT"),
                Map.entry("birth_date", "2000-01-01"),
                Map.entry("graduation_date", "2025-02"),
                Map.entry("grad_school_plan", false),
                Map.entry("answers", List.of(
                        Map.of("question_id", commonQId, "answer", "공통 답변"),
                        Map.of("question_id", trackQId, "answer", "트랙 답변")))
        );
        return objectMapper.convertValue(req, ApplicationRequest.class);
    }

    @Nested
    @DisplayName("모집 상태 조회")
    class Status {

        @Test
        @DisplayName("모집 중 공고가 있으면 isActive=true, term 반환")
        void active() {
            saveActiveRecruitment(27);
            em.flush();
            em.clear();

            RecruitmentStatusResponse res = recruitmentService.getRecruitmentStatus();

            assertThat(res.getIsActive()).isTrue();
            assertThat(res.getTerm()).isEqualTo(27);
        }

        @Test
        @DisplayName("공고가 없으면 isActive=false, term=null")
        void inactive() {
            RecruitmentStatusResponse res = recruitmentService.getRecruitmentStatus();

            assertThat(res.getIsActive()).isFalse();
            assertThat(res.getTerm()).isNull();
        }
    }

    @Nested
    @DisplayName("사전 알림 신청")
    class Subscribe {

        @Test
        @DisplayName("신규 이메일 신청 후 DB 저장 + 중복 신청 시 DUPLICATE_EMAIL")
        void subscribeAndDuplicate() {
            SubscriptionRequest request = objectMapper.convertValue(
                    Map.of("email", "new@example.com"), SubscriptionRequest.class);

            SubscriptionResponse res = recruitmentService.subscribe(request);
            em.flush();

            assertThat(res.getEmail()).isEqualTo("new@example.com");
            assertThat(subscriptionRepository.existsByEmail("new@example.com")).isTrue();

            assertThatThrownBy(() -> recruitmentService.subscribe(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Nested
    @DisplayName("지원서 제출 end-to-end")
    class Submit {

        @Test
        @DisplayName("신규 제출 → Applicant(SUBMITTED) + 답변이 실제 DB에 저장")
        void submitNew() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            Long commonQ = saveQuestion(r, Category.COMMON, 1).getId();
            Long trackQ = saveQuestion(r, Category.ENGINEERING, 2).getId();
            em.flush();
            em.clear();

            ApplicationResponse res = recruitmentService.submitApplication(
                    u.getId(), r.getId(), buildSubmitRequest(Track.ENGINEERING, commonQ, trackQ));
            em.flush();
            em.clear();

            Applicant saved = applicantRepository.findById(res.getApplicantId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(Applicant.ApplicantStatus.SUBMITTED);
            assertThat(saved.getSubmittedAt()).isNotNull();
            assertThat(answerRepository.findByApplicantIds(List.of(saved.getId()))).hasSize(2);
        }

        @Test
        @DisplayName("DRAFT 존재 시 제출 → 같은 레코드가 SUBMITTED 로 전환")
        void draftToSubmitted() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            Long commonQ = saveQuestion(r, Category.COMMON, 1).getId();
            Long trackQ = saveQuestion(r, Category.ENGINEERING, 2).getId();
            Applicant draft = applicantRepository.save(Applicant.builder()
                    .recruitment(r).user(u).status(Applicant.ApplicantStatus.DRAFT)
                    .track(Track.ENGINEERING).name("임시").email("temp@example.com").phone("01000000000")
                    .build());
            Long draftId = draft.getId();
            em.flush();
            em.clear();

            ApplicationResponse res = recruitmentService.submitApplication(
                    u.getId(), r.getId(), buildSubmitRequest(Track.ENGINEERING, commonQ, trackQ));
            em.flush();

            assertThat(res.getApplicantId()).isEqualTo(draftId);
            assertThat(applicantRepository.findById(draftId).orElseThrow().getStatus())
                    .isEqualTo(Applicant.ApplicantStatus.SUBMITTED);
            assertThat(applicantRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 SUBMITTED 상태에서 재제출 → ALREADY_SUBMITTED")
        void alreadySubmitted() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            Long commonQ = saveQuestion(r, Category.COMMON, 1).getId();
            Long trackQ = saveQuestion(r, Category.ENGINEERING, 2).getId();
            Applicant submitted = Applicant.builder()
                    .recruitment(r).user(u).status(Applicant.ApplicantStatus.SUBMITTED)
                    .track(Track.ENGINEERING).name("홍길동").email("hong@example.com").phone("01012345678")
                    .build();
            submitted.markSubmitted();
            applicantRepository.save(submitted);
            em.flush();
            em.clear();

            assertThatThrownBy(() -> recruitmentService.submitApplication(
                    u.getId(), r.getId(), buildSubmitRequest(Track.ENGINEERING, commonQ, trackQ)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_SUBMITTED);
        }
    }

    @Nested
    @DisplayName("어드민 흐름")
    class Admin {

        @Test
        @DisplayName("마감된 공고의 지원서 전체 삭제 → answer/applicant 모두 제거")
        void deleteApplicants() {
            Recruitment r = saveClosedRecruitment(26);
            User u = saveUser();
            ApplicationQuestion q = saveQuestion(r, Category.COMMON, 1);
            Applicant a = applicantRepository.save(Applicant.builder()
                    .recruitment(r).user(u).status(Applicant.ApplicantStatus.SUBMITTED)
                    .track(Track.ENGINEERING).name("n").email("a@example.com").phone("01012345678")
                    .build());
            answerRepository.save(com.boaz.backend.domain.recruitment.entity.ApplicantAnswer.builder()
                    .applicant(a).question(q).answerText("답").build());
            em.flush();
            em.clear();

            recruitmentService.deleteApplicants(r.getId());
            em.flush();
            em.clear();

            assertThat(applicantRepository.existsByRecruitmentId(r.getId())).isFalse();
            assertThat(answerRepository.findByApplicantIds(List.of(a.getId()))).isEmpty();
        }

        @Test
        @DisplayName("모집 진행 중 공고의 지원서 삭제 시도 → RECRUITMENT_NOT_CLOSED")
        void deleteWhileActive() {
            Recruitment r = saveActiveRecruitment(27);
            em.flush();

            assertThatThrownBy(() -> recruitmentService.deleteApplicants(r.getId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RECRUITMENT_NOT_CLOSED);
        }

        @Test
        @DisplayName("구독 목록 최신순 조회 후 전체 삭제")
        void subscriptionsLifecycle() {
            subscriptionRepository.save(com.boaz.backend.domain.recruitment.entity.Subscription.builder()
                    .email("a@example.com").build());
            subscriptionRepository.save(com.boaz.backend.domain.recruitment.entity.Subscription.builder()
                    .email("b@example.com").build());
            em.flush();

            List<SubscriptionResponse> list = recruitmentService.getAllSubscriptions();
            assertThat(list).hasSize(2);

            recruitmentService.deleteAllSubscriptions();
            em.flush();

            assertThat(subscriptionRepository.count()).isZero();
        }
    }
}
