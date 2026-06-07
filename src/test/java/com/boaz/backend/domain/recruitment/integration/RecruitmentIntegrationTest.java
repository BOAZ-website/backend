package com.boaz.backend.domain.recruitment.integration;

import com.boaz.backend.domain.recruitment.dto.request.ApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.DraftApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.SubscriptionRequest;
import com.boaz.backend.domain.recruitment.dto.response.ApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.DeadlineResponse;
import com.boaz.backend.domain.recruitment.dto.response.DraftApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.MyApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.QuestionResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentStatusResponse;
import com.boaz.backend.domain.recruitment.dto.response.SubscriptionResponse;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    @MockitoBean S3Service s3Service;

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

    // label 컬럼은 length=20 제약이 있어 짧게 생성 (예: q-C1, q-E2)
    private String shortLabel(String prefix, Category category, int orderNum) {
        return prefix + category.name().charAt(0) + orderNum;
    }

    private ApplicationQuestion saveQuestion(Recruitment r, Category category, int orderNum) {
        return questionRepository.save(ApplicationQuestion.create(
                r, shortLabel("q-", category, orderNum), category, Type.TEXT,
                "content", null, 500, null, orderNum, true));
    }

    private ApplicationQuestion saveQuestion(Recruitment r, Category category, int orderNum, String content) {
        return questionRepository.save(ApplicationQuestion.create(
                r, shortLabel("q-", category, orderNum), category, Type.TEXT,
                content, null, 500, null, orderNum, true));
    }

    private ApplicationQuestion saveTableQuestion(Recruitment r, Category category, int orderNum) {
        return questionRepository.save(ApplicationQuestion.create(
                r, shortLabel("qt-", category, orderNum), category, Type.TABLE,
                "테이블 질문", null, null, "{\"rows\":[\"A\"],\"columns\":[\"B\"]}", orderNum, true));
    }

    private ApplicationQuestion saveMultiTableQuestion(Recruitment r, Category category, int orderNum) {
        return questionRepository.save(ApplicationQuestion.create(
                r, shortLabel("qm-", category, orderNum), category, Type.TABLE,
                "복수선택 질문", null, null,
                "{\"rows\":[\"1월 4일\",\"1월 5일\"],\"columns\":[\"12:00~14:00\",\"14:00~16:00\"],\"multiple\":true}",
                orderNum, true));
    }

    // 제출 요청의 개인정보 공통 필드 (answers 제외)
    private Map<String, Object> personalFields(Track track) {
        Map<String, Object> m = new HashMap<>();
        m.put("track", track.name());
        m.put("name", "홍길동");
        m.put("email", "hong@example.com");
        m.put("phone", "01012345678");
        m.put("university", "한국대학교");
        m.put("major", "컴퓨터공학");
        m.put("last_semester", 4);
        m.put("military_status", "COMPLETED_OR_EXEMPT");
        m.put("birth_date", "2000-01-01");
        m.put("graduation_date", "2025-02");
        m.put("grad_school_plan", false);
        return m;
    }

    private DraftApplicationRequest buildDraftRequest(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, DraftApplicationRequest.class);
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

        @Test
        @DisplayName("TABLE 답변 → JSON 컬럼에 정상 직렬화 저장")
        void submitWithTableAnswer() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            Long commonQ = saveQuestion(r, Category.COMMON, 1).getId();
            Long tableQ = saveTableQuestion(r, Category.ENGINEERING, 2).getId();
            em.flush();
            em.clear();

            Map<String, Object> fields = personalFields(Track.ENGINEERING);
            fields.put("answers", List.of(
                    Map.of("question_id", commonQ, "answer", "공통 답변"),
                    Map.of("question_id", tableQ, "answer", Map.of("키", "값"))));
            ApplicationRequest req = objectMapper.convertValue(fields, ApplicationRequest.class);

            ApplicationResponse res = recruitmentService.submitApplication(u.getId(), r.getId(), req);
            em.flush();
            em.clear();

            List<ApplicantAnswer> answers = answerRepository.findByApplicantIds(List.of(res.getApplicantId()));
            assertThat(answers).hasSize(2);
            ApplicantAnswer tableAnswer = answers.stream()
                    .filter(a -> a.getAnswerJson() != null).findFirst().orElseThrow();
            assertThat(tableAnswer.getAnswerJson()).contains("키").contains("값");
        }

        @Test
        @DisplayName("복수선택 TABLE 답변 → answerJson에 배열로 직렬화 저장")
        void submitWithMultiTableAnswer() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            Long commonQ = saveQuestion(r, Category.COMMON, 1).getId();
            Long multiQ = saveMultiTableQuestion(r, Category.COMMON, 2).getId();
            em.flush();
            em.clear();

            Map<String, Object> fields = personalFields(Track.ENGINEERING);
            fields.put("answers", List.of(
                    Map.of("question_id", commonQ, "answer", "공통 답변"),
                    Map.of("question_id", multiQ, "answer",
                            Map.of("1월 4일", List.of("12:00~14:00", "14:00~16:00"),
                                   "1월 5일", List.of()))));
            ApplicationRequest req = objectMapper.convertValue(fields, ApplicationRequest.class);

            ApplicationResponse res = recruitmentService.submitApplication(u.getId(), r.getId(), req);
            em.flush();
            em.clear();

            List<ApplicantAnswer> answers = answerRepository.findByApplicantIds(List.of(res.getApplicantId()));
            ApplicantAnswer multiAnswer = answers.stream()
                    .filter(a -> a.getAnswerJson() != null).findFirst().orElseThrow();
            assertThat(multiAnswer.getAnswerJson()).contains("[");
            assertThat(multiAnswer.getAnswerJson()).contains("12:00~14:00");
            assertThat(multiAnswer.getAnswerJson()).contains("14:00~16:00");
        }

        @Test
        @DisplayName("복수선택 TABLE 중복 원소 → dedupe 후 저장")
        void submitMultiTableDeduped() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            Long commonQ = saveQuestion(r, Category.COMMON, 1).getId();
            Long multiQ = saveMultiTableQuestion(r, Category.COMMON, 2).getId();
            em.flush();
            em.clear();

            Map<String, Object> fields = personalFields(Track.ENGINEERING);
            fields.put("answers", List.of(
                    Map.of("question_id", commonQ, "answer", "공통 답변"),
                    Map.of("question_id", multiQ, "answer",
                            Map.of("1월 4일", List.of("12:00~14:00", "12:00~14:00")))));
            ApplicationRequest req = objectMapper.convertValue(fields, ApplicationRequest.class);

            ApplicationResponse res = recruitmentService.submitApplication(u.getId(), r.getId(), req);
            em.flush();
            em.clear();

            ApplicantAnswer multiAnswer = answerRepository.findByApplicantIds(List.of(res.getApplicantId()))
                    .stream().filter(a -> a.getAnswerJson() != null).findFirst().orElseThrow();
            // "12:00~14:00"이 딱 한 번만 나와야 함
            assertThat(multiAnswer.getAnswerJson().split("12:00~14:00", -1).length - 1).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("기수별 공고 조회 end-to-end (REC-002)")
    class GetRecruitment {

        @Test
        @DisplayName("존재하는 term → 공고 반환 (isActive 날짜 계산)")
        void found() {
            saveActiveRecruitment(27);
            em.flush();
            em.clear();

            RecruitmentResponse res = recruitmentService.getRecruitment(27);

            assertThat(res.getTerm()).isEqualTo(27);
            assertThat(res.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("마감된 공고 → isActive=false (404 아님)")
        void closedStillReturns() {
            saveClosedRecruitment(26);
            em.flush();
            em.clear();

            RecruitmentResponse res = recruitmentService.getRecruitment(26);

            assertThat(res.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 term → RECRUITMENT_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> recruitmentService.getRecruitment(999))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("질문 목록 조회 end-to-end (REC-003)")
    class GetQuestions {

        @Test
        @DisplayName("COMMON + 지정 트랙만 order_num 순 반환, {Track} 치환")
        void filteredAndOrdered() {
            Recruitment r = saveActiveRecruitment(27);
            saveQuestion(r, Category.COMMON, 1);
            saveQuestion(r, Category.ENGINEERING, 2, "{Track} 지원 동기");
            saveQuestion(r, Category.VISUALIZATION, 3);
            em.flush();
            em.clear();

            List<QuestionResponse> result = recruitmentService.getQuestions(r.getId(), Track.ENGINEERING);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(QuestionResponse::getOrderNum).containsExactly(1, 2);
            assertThat(result).noneMatch(q -> q.getCategory().equals("VISUALIZATION"));
            assertThat(result.get(1).getContent()).contains("엔지니어링");
        }

        @Test
        @DisplayName("모집 기간 외 → RECRUITMENT_NOT_AVAILABLE")
        void notAvailable() {
            Recruitment r = saveClosedRecruitment(26);
            em.flush();

            assertThatThrownBy(() -> recruitmentService.getQuestions(r.getId(), Track.ENGINEERING))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RECRUITMENT_NOT_AVAILABLE);
        }
    }

    @Nested
    @DisplayName("마감 일시 조회 end-to-end (REC-006)")
    class GetDeadline {

        @Test
        @DisplayName("모집 중 공고 → end_date 반환")
        void found() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime end = now.plusDays(1).withNano(0);
            Recruitment r = recruitmentRepository.save(
                    Recruitment.create(27, now.minusDays(1), end, "{}", null));
            em.flush();
            em.clear();

            DeadlineResponse res = recruitmentService.getDeadline();

            assertThat(res.getDeadline()).isEqualToIgnoringNanos(r.getEndDate());
        }

        @Test
        @DisplayName("활성 공고 없음 → RECRUITMENT_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> recruitmentService.getDeadline())
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("임시저장 end-to-end (REC-007)")
    class SaveDraft {

        @Test
        @DisplayName("신규 생성 후 부분 업데이트 → 같은 레코드 유지, name 보존 / track 갱신")
        void createThenPartialUpdate() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            em.flush();
            em.clear();

            DraftApplicationResponse first = recruitmentService.saveDraft(
                    u.getId(), r.getId(), buildDraftRequest(Map.of("name", "홍길동")));
            em.flush();
            em.clear();

            DraftApplicationResponse second = recruitmentService.saveDraft(
                    u.getId(), r.getId(), buildDraftRequest(Map.of("track", "ENGINEERING")));
            em.flush();
            em.clear();

            assertThat(second.getApplicantId()).isEqualTo(first.getApplicantId());
            assertThat(applicantRepository.count()).isEqualTo(1);
            Applicant saved = applicantRepository.findById(first.getApplicantId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(Applicant.ApplicantStatus.DRAFT);
            assertThat(saved.getName()).isEqualTo("홍길동");          // null 필드라 유지
            assertThat(saved.getTrack()).isEqualTo(Track.ENGINEERING); // 새로 들어온 값으로 갱신
        }

        @Test
        @DisplayName("answers 분기: 값 교체 → null 유지 → [] 전삭제")
        void answersBranching() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            Long q1 = saveQuestion(r, Category.COMMON, 1).getId();
            em.flush();
            em.clear();

            // 1) 값 있는 answers → 1건 저장
            recruitmentService.saveDraft(u.getId(), r.getId(), buildDraftRequest(Map.of(
                    "answers", List.of(Map.of("question_id", q1, "answer", "초기 답변")))));
            em.flush();
            em.clear();
            Long applicantId = applicantRepository.findByRecruitmentIdAndUserId(r.getId(), u.getId())
                    .orElseThrow().getId();
            assertThat(answerRepository.findByApplicantIds(List.of(applicantId))).hasSize(1);

            // 2) answers 미포함(null) → 기존 답변 유지
            recruitmentService.saveDraft(u.getId(), r.getId(), buildDraftRequest(Map.of("name", "홍길동")));
            em.flush();
            em.clear();
            assertThat(answerRepository.findByApplicantIds(List.of(applicantId))).hasSize(1);

            // 3) answers=[] → 전체 삭제
            recruitmentService.saveDraft(u.getId(), r.getId(), buildDraftRequest(Map.of(
                    "answers", List.of())));
            em.flush();
            em.clear();
            assertThat(answerRepository.findByApplicantIds(List.of(applicantId))).isEmpty();
        }
    }

    @Nested
    @DisplayName("내 지원서 조회 end-to-end (REC-008)")
    class GetMyApplication {

        @Test
        @DisplayName("DRAFT + 답변 → answers 역직렬화 포함 응답 반환")
        void draftWithAnswers() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            Long q1 = saveQuestion(r, Category.COMMON, 1).getId();
            em.flush();
            em.clear();

            recruitmentService.saveDraft(u.getId(), r.getId(), buildDraftRequest(Map.of(
                    "name", "홍길동",
                    "answers", List.of(Map.of("question_id", q1, "answer", "내 답변")))));
            em.flush();
            em.clear();

            MyApplicationResponse res = recruitmentService.getMyApplication(u.getId(), r.getId());

            assertThat(res.getStatus()).isEqualTo(Applicant.ApplicantStatus.DRAFT);
            assertThat(res.getName()).isEqualTo("홍길동");
            assertThat(res.getAnswers()).hasSize(1);
            assertThat(res.getAnswers().get(0).getAnswer().asText()).isEqualTo("내 답변");
        }

        @Test
        @DisplayName("SUBMITTED 상태 → APPLICATION_ALREADY_SUBMITTED")
        void submittedForbidden() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            applicantRepository.save(Applicant.builder()
                    .recruitment(r).user(u).status(Applicant.ApplicantStatus.SUBMITTED)
                    .track(Track.ENGINEERING).name("n").email("a@example.com").phone("01012345678")
                    .build());
            em.flush();
            em.clear();

            assertThatThrownBy(() -> recruitmentService.getMyApplication(u.getId(), r.getId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_ALREADY_SUBMITTED);
        }
    }

    @Nested
    @DisplayName("CSV 생성 end-to-end (REC-ADMIN-001)")
    class DownloadApplications {

        @Test
        @DisplayName("부문 3개 모두 S3 업로드 호출 (빈 부문 포함)")
        void uploadsThreeTracks() {
            Recruitment r = saveActiveRecruitment(27);
            User u = saveUser();
            saveQuestion(r, Category.COMMON, 1);
            Applicant a = applicantRepository.save(Applicant.builder()
                    .recruitment(r).user(u).status(Applicant.ApplicantStatus.SUBMITTED)
                    .track(Track.ENGINEERING).name("n").email("a@example.com").phone("01012345678")
                    .build());
            a.markSubmitted();
            em.flush();
            em.clear();

            recruitmentService.downloadApplications(27);

            verify(s3Service, times(3)).uploadCsv(any(), any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 term → RECRUITMENT_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> recruitmentService.downloadApplications(999))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
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
