package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.recruitment.dto.request.AnswerRequest;
import com.boaz.backend.domain.recruitment.dto.request.ApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.DraftApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.SubscriptionRequest;
import com.boaz.backend.domain.recruitment.dto.response.*;
import com.boaz.backend.domain.recruitment.entity.*;
import com.boaz.backend.domain.recruitment.repository.*;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.util.S3Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecruitmentServiceTest {

    @InjectMocks
    RecruitmentService recruitmentService;

    @Mock RecruitmentRepository recruitmentRepository;
    @Mock ApplicationQuestionRepository applicationQuestionRepository;
    @Mock ApplicantRepository applicantRepository;
    @Mock ApplicantAnswerRepository applicantAnswerRepository;
    @Mock UserRepository userRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock S3Service s3Service;
    @Mock CsvService csvService;
    @Spy  ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recruitmentService, "recruitmentBucket", "test-bucket");
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private Recruitment createActiveRecruitment() {
        Recruitment r = Recruitment.create(27,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                "[]", "https://example.com/brochure.pdf");
        ReflectionTestUtils.setField(r, "id", 1L);
        return r;
    }

    private Recruitment createExpiredRecruitment() {
        Recruitment r = Recruitment.create(26,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1),
                "[]", null);
        ReflectionTestUtils.setField(r, "id", 2L);
        return r;
    }

    private User createUser(Long id) {
        User u = User.builder()
                .provider("kakao").providerId("test-" + id)
                .nickname("홍길동").memberType(MemberType.OUTSIDER)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private ApplicationQuestion createTextQuestion(Long id, Recruitment r,
            ApplicationQuestion.Category category, int orderNum, boolean required) {
        ApplicationQuestion q = ApplicationQuestion.create(
                r, "label-" + id, category,
                ApplicationQuestion.Type.TEXT, "질문내용 " + id,
                500, null, orderNum, required);
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private ApplicationQuestion createTableQuestion(Long id, Recruitment r,
            ApplicationQuestion.Category category, int orderNum, boolean required) {
        ApplicationQuestion q = ApplicationQuestion.create(
                r, "label-t-" + id, category,
                ApplicationQuestion.Type.TABLE, "테이블질문 " + id,
                null, "{\"rows\":[\"A\"],\"columns\":[\"B\"]}", orderNum, required);
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private Applicant createDraftApplicant(Recruitment r, User u) {
        Applicant a = Applicant.builder()
                .recruitment(r).user(u)
                .status(Applicant.ApplicantStatus.DRAFT)
                .track(Track.ENGINEERING)
                .name("홍길동").email("hong@example.com")
                .build();
        ReflectionTestUtils.setField(a, "id", 42L);
        return a;
    }

    private Applicant createSubmittedApplicant(Recruitment r, User u) {
        Applicant a = Applicant.builder()
                .recruitment(r).user(u)
                .status(Applicant.ApplicantStatus.SUBMITTED)
                .track(Track.ENGINEERING)
                .name("홍길동").email("hong@example.com")
                .phone("01012345678").university("한국대")
                .major("컴공").build();
        ReflectionTestUtils.setField(a, "id", 42L);
        ReflectionTestUtils.setField(a, "submittedAt", LocalDateTime.now());
        return a;
    }

    private ApplicationRequest buildApplicationRequest(List<AnswerRequest> answers) {
        ApplicationRequest req = new ApplicationRequest();
        ReflectionTestUtils.setField(req, "track", Track.ENGINEERING);
        ReflectionTestUtils.setField(req, "name", "홍길동");
        ReflectionTestUtils.setField(req, "email", "hong@example.com");
        ReflectionTestUtils.setField(req, "phone", "01012345678");
        ReflectionTestUtils.setField(req, "university", "한국대학교");
        ReflectionTestUtils.setField(req, "major", "컴퓨터공학");
        ReflectionTestUtils.setField(req, "lastSemester", 6);
        ReflectionTestUtils.setField(req, "militaryStatus", Applicant.MilitaryStatus.COMPLETED_OR_EXEMPT);
        ReflectionTestUtils.setField(req, "birthDate", "2000-01-01");
        ReflectionTestUtils.setField(req, "graduationDate", "2026-02");
        ReflectionTestUtils.setField(req, "gradSchoolPlan", false);
        ReflectionTestUtils.setField(req, "answers", answers);
        return req;
    }

    private AnswerRequest buildTextAnswer(Long questionId, String text) {
        AnswerRequest a = new AnswerRequest();
        ReflectionTestUtils.setField(a, "questionId", questionId);
        ReflectionTestUtils.setField(a, "answer", TextNode.valueOf(text));
        return a;
    }

    private AnswerRequest buildJsonAnswer(Long questionId, JsonNode node) {
        AnswerRequest a = new AnswerRequest();
        ReflectionTestUtils.setField(a, "questionId", questionId);
        ReflectionTestUtils.setField(a, "answer", node);
        return a;
    }

    private Subscription createSubscription(Long id, String email) {
        Subscription s = Subscription.builder().email(email).build();
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    // ══════════════════════════════════════════════
    // REC-001: 모집 중 여부 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-001 모집 중 여부 조회")
    class GetRecruitmentStatus {

        @Test
        @DisplayName("TC-001 모집 기간 중 → isActive:true, term 반환")
        void active() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findActiveRecruitment(any())).thenReturn(Optional.of(r));

            RecruitmentStatusResponse res = recruitmentService.getRecruitmentStatus();

            assertThat(res.getIsActive()).isTrue();
            assertThat(res.getTerm()).isEqualTo(27);
        }

        @Test
        @DisplayName("TC-002 모집 기간 외 공고만 있을 때 → isActive:false")
        void expiredRecruitment() {
            when(recruitmentRepository.findActiveRecruitment(any())).thenReturn(Optional.empty());

            RecruitmentStatusResponse res = recruitmentService.getRecruitmentStatus();

            assertThat(res.getIsActive()).isFalse();
            assertThat(res.getTerm()).isNull();
        }

        @Test
        @DisplayName("TC-003 공고 데이터 없음 → isActive:false (예외 없음)")
        void noData() {
            when(recruitmentRepository.findActiveRecruitment(any())).thenReturn(Optional.empty());

            RecruitmentStatusResponse res = recruitmentService.getRecruitmentStatus();

            assertThat(res.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("TC-004 start_date 정각 → isActive:true")
        void startDateBoundary() {
            LocalDateTime now = LocalDateTime.now();
            Recruitment r = Recruitment.create(27, now, now.plusDays(7), "[]", null);
            ReflectionTestUtils.setField(r, "id", 1L);
            when(recruitmentRepository.findActiveRecruitment(any())).thenReturn(Optional.of(r));

            assertThat(recruitmentService.getRecruitmentStatus().getIsActive()).isTrue();
        }

        @Test
        @DisplayName("TC-005 end_date 1초 초과 → isActive:false")
        void endDatePast() {
            when(recruitmentRepository.findActiveRecruitment(any())).thenReturn(Optional.empty());

            assertThat(recruitmentService.getRecruitmentStatus().getIsActive()).isFalse();
        }
    }

    // ══════════════════════════════════════════════
    // REC-002: 기수별 모집 공고 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-002 기수별 모집 공고 조회")
    class GetRecruitment {

        @Test
        @DisplayName("TC-001 존재하는 기수 조회 → 공고 반환")
        void found() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findByTerm(27)).thenReturn(Optional.of(r));

            RecruitmentResponse res = recruitmentService.getRecruitment(27);

            assertThat(res.getTerm()).isEqualTo(27);
            assertThat(res.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("TC-002 모집 마감된 공고 조회 → isActive:false (404 아님)")
        void expiredRecruitment() {
            Recruitment r = createExpiredRecruitment();
            when(recruitmentRepository.findByTerm(26)).thenReturn(Optional.of(r));

            RecruitmentResponse res = recruitmentService.getRecruitment(26);

            assertThat(res.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("TC-003 존재하지 않는 기수 → RECRUITMENT_NOT_FOUND")
        void notFound() {
            when(recruitmentRepository.findByTerm(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getRecruitment(999))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // REC-003: 지원서 질문 목록 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-003 지원서 질문 목록 조회")
    class GetQuestions {

        @Test
        @DisplayName("TC-001 ENGINEERING 트랙 → COMMON + ENGINEERING 질문 반환 (order_num 오름차순)")
        void engineering() {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q1 = createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true);
            ApplicationQuestion q2 = createTextQuestion(2L, r, ApplicationQuestion.Category.COMMON, 2, true);
            ApplicationQuestion q3 = createTextQuestion(3L, r, ApplicationQuestion.Category.ENGINEERING, 3, true);

            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(
                    eq(1L), eq(ApplicationQuestion.Category.COMMON), eq(ApplicationQuestion.Category.ENGINEERING)))
                    .thenReturn(List.of(q1, q2, q3));

            List<QuestionResponse> result = recruitmentService.getQuestions(1L, Track.ENGINEERING);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getOrderNum()).isLessThanOrEqualTo(result.get(1).getOrderNum());
        }

        @Test
        @DisplayName("TC-002 모집 기간 외 → RECRUITMENT_NOT_AVAILABLE")
        void notInPeriod() {
            Recruitment r = createExpiredRecruitment();
            when(recruitmentRepository.findById(2L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> recruitmentService.getQuestions(2L, Track.ANALYSIS))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("TC-003 존재하지 않는 공고 → RECRUITMENT_NOT_FOUND")
        void noRecruitment() {
            when(recruitmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getQuestions(999L, Track.ENGINEERING))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-004 질문 없음 → QUESTIONS_NOT_FOUND")
        void noQuestions() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> recruitmentService.getQuestions(1L, Track.ENGINEERING))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.QUESTIONS_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-005 track=ALL → INVALID_TRACK_SELECTION")
        void trackAll() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> recruitmentService.getQuestions(1L, Track.ALL))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
        }
    }

    // ══════════════════════════════════════════════
    // REC-004: 지원서 제출
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-004 지원서 제출")
    class SubmitApplication {

        private Recruitment activeRecruitment;
        private User user;
        private ApplicationQuestion q1;

        @BeforeEach
        void setUp() {
            activeRecruitment = createActiveRecruitment();
            user = createUser(1L);
            q1 = createTextQuestion(1L, activeRecruitment, ApplicationQuestion.Category.COMMON, 1, true);
        }

        @Test
        @DisplayName("TC-001 신규 지원서 제출 → SUBMITTED 생성, 201")
        void newSubmit() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1));
            when(applicantRepository.save(any())).thenAnswer(inv -> {
                Applicant a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 42L);
                return a;
            });

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변내용")));
            ApplicationResponse res = recruitmentService.submitApplication(1L, 1L, req);

            assertThat(res.getApplicantId()).isEqualTo(42L);
            verify(applicantRepository).save(any(Applicant.class));
        }

        @Test
        @DisplayName("TC-002 DRAFT 존재 시 제출 → SUBMITTED 전환 (새 레코드 생성 안 함)")
        void draftToSubmitted() {
            Applicant draft = createDraftApplicant(activeRecruitment, user);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1));

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변내용")));
            ApplicationResponse res = recruitmentService.submitApplication(1L, 1L, req);

            assertThat(res.getApplicantId()).isEqualTo(42L);
            verify(applicantAnswerRepository).deleteByApplicantId(42L);
            verify(applicantRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-003 이미 SUBMITTED → ALREADY_SUBMITTED")
        void alreadySubmitted() {
            Applicant submitted = createSubmittedApplicant(activeRecruitment, user);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(submitted));

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변")));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ALREADY_SUBMITTED);
        }

        @Test
        @DisplayName("TC-004 모집 기간 외 → RECRUITMENT_CLOSED")
        void closed() {
            Recruitment expired = createExpiredRecruitment();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(expired));

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변")));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_CLOSED);
        }

        @Test
        @DisplayName("TC-005 이메일 형식 오류 → INVALID_EMAIL_FORMAT")
        void invalidEmail() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변")));
            ReflectionTestUtils.setField(req, "email", "invalidemail");

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        @Test
        @DisplayName("TC-006 전화번호 형식 오류(하이픈) → INVALID_PHONE_FORMAT")
        void invalidPhoneHyphen() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변")));
            ReflectionTestUtils.setField(req, "phone", "010-1234-5678");

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_PHONE_FORMAT);
        }

        @Test
        @DisplayName("TC-006 전화번호 형식 오류(9자리) → INVALID_PHONE_FORMAT")
        void invalidPhoneTooShort() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변")));
            ReflectionTestUtils.setField(req, "phone", "010123456");

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_PHONE_FORMAT);
        }

        @Test
        @DisplayName("TC-007 생년월일 형식 오류 → INVALID_BIRTH_DATE_FORMAT")
        void invalidBirthDate() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변")));
            ReflectionTestUtils.setField(req, "birthDate", "20000101");

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_BIRTH_DATE_FORMAT);
        }

        @Test
        @DisplayName("TC-008 필수 질문 답변 누락 → ANSWER_REQUIRED")
        void missingRequired() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            ApplicationQuestion q2 = createTextQuestion(2L, activeRecruitment, ApplicationQuestion.Category.COMMON, 2, true);
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, q2));

            // q2(필수) 답변 누락
            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변")));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ANSWER_REQUIRED);
        }

        @Test
        @DisplayName("TC-009 유효하지 않은 questionId → INVALID_INPUT_VALUE")
        void invalidQuestionId() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1));

            // q1 답변 + 유효하지 않은 999번 답변 포함
            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "답변"), buildTextAnswer(999L, "잘못된답변")));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("TC-010 중복 questionId → INVALID_INPUT_VALUE")
        void duplicateQuestionId() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1));

            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "첫번째"), buildTextAnswer(1L, "중복")));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("TC-011 TEXT 질문(is_required=false)에 객체 답변 → INVALID_ANSWER_TYPE")
        void invalidAnswerType() throws Exception {
            ApplicationQuestion optional = createTextQuestion(2L, activeRecruitment,
                    ApplicationQuestion.Category.COMMON, 2, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, optional));

            JsonNode objNode = objectMapper.readTree("{\"key\":\"value\"}");
            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "정상답변"),
                            buildJsonAnswer(2L, objNode))); // TEXT 질문에 객체

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ANSWER_TYPE);
        }
    }

    // ══════════════════════════════════════════════
    // REC-005: 모집 사전 알림 신청
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-005 모집 사전 알림 신청")
    class Subscribe {

        @Test
        @DisplayName("TC-001 신규 이메일 → 201, Subscription 저장")
        void newEmail() {
            Subscription s = createSubscription(1L, "new@example.com");
            when(subscriptionRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(subscriptionRepository.saveAndFlush(any())).thenReturn(s);

            SubscriptionRequest req = new SubscriptionRequest();
            ReflectionTestUtils.setField(req, "email", "new@example.com");

            SubscriptionResponse res = recruitmentService.subscribe(req);

            assertThat(res.getEmail()).isEqualTo("new@example.com");
        }

        @Test
        @DisplayName("TC-002 중복 이메일 → DUPLICATE_EMAIL")
        void duplicateEmail() {
            when(subscriptionRepository.existsByEmail("dup@example.com")).thenReturn(true);

            SubscriptionRequest req = new SubscriptionRequest();
            ReflectionTestUtils.setField(req, "email", "dup@example.com");

            assertThatThrownBy(() -> recruitmentService.subscribe(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        }

        @Test
        @DisplayName("TC-003 이메일 형식 오류 → INVALID_EMAIL_FORMAT")
        void invalidFormat() {
            SubscriptionRequest req = new SubscriptionRequest();
            ReflectionTestUtils.setField(req, "email", "notanemail");

            assertThatThrownBy(() -> recruitmentService.subscribe(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        @Test
        @DisplayName("TC-004 레이스 컨디션(DataIntegrityViolation) → DUPLICATE_EMAIL")
        void racingCondition() {
            when(subscriptionRepository.existsByEmail("race@example.com")).thenReturn(false);
            when(subscriptionRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("unique constraint"));

            SubscriptionRequest req = new SubscriptionRequest();
            ReflectionTestUtils.setField(req, "email", "race@example.com");

            assertThatThrownBy(() -> recruitmentService.subscribe(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    // ══════════════════════════════════════════════
    // REC-006: 모집 마감 일시 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-006 모집 마감 일시 조회")
    class GetDeadline {

        @Test
        @DisplayName("TC-001 모집 중인 공고 존재 → deadline 반환")
        void found() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findActiveRecruitment(any())).thenReturn(Optional.of(r));

            DeadlineResponse res = recruitmentService.getDeadline();

            assertThat(res.getDeadline()).isEqualTo(r.getEndDate());
        }

        @Test
        @DisplayName("TC-002 모집 중인 공고 없음 → RECRUITMENT_NOT_FOUND")
        void notFound() {
            when(recruitmentRepository.findActiveRecruitment(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getDeadline())
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // REC-007: 지원서 임시저장
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-007 지원서 임시저장")
    class SaveDraft {

        private Recruitment active;
        private User user;

        @BeforeEach
        void setUp() {
            active = createActiveRecruitment();
            user = createUser(1L);
        }

        @Test
        @DisplayName("TC-001 기존 지원서 없음 → DRAFT 신규 생성")
        void createNew() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicantRepository.save(any())).thenAnswer(inv -> {
                Applicant a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 42L);
                return a;
            });

            DraftApplicationRequest req = new DraftApplicationRequest();
            ReflectionTestUtils.setField(req, "name", "홍길동");
            ReflectionTestUtils.setField(req, "email", "hong@example.com");

            DraftApplicationResponse res = recruitmentService.saveDraft(1L, 1L, req);

            assertThat(res.getApplicantId()).isEqualTo(42L);
            verify(applicantRepository).save(any(Applicant.class));
        }

        @Test
        @DisplayName("TC-002 기존 DRAFT 존재 → 부분 업데이트 (null 필드 유지)")
        void updateDraft() {
            Applicant draft = createDraftApplicant(active, user);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));

            DraftApplicationRequest req = new DraftApplicationRequest();
            ReflectionTestUtils.setField(req, "name", "새이름");

            DraftApplicationResponse res = recruitmentService.saveDraft(1L, 1L, req);

            assertThat(res.getApplicantId()).isEqualTo(42L);
            verify(applicantRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-003 answers=null → 기존 answers 변경 없음")
        void answersNull() {
            Applicant draft = createDraftApplicant(active, user);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));

            DraftApplicationRequest req = new DraftApplicationRequest();
            ReflectionTestUtils.setField(req, "answers", null);

            recruitmentService.saveDraft(1L, 1L, req);

            verify(applicantAnswerRepository, never()).deleteByApplicantId(any());
        }

        @Test
        @DisplayName("TC-004 answers=[] → 기존 answers 전부 삭제")
        void answersEmpty() {
            Applicant draft = createDraftApplicant(active, user);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));

            DraftApplicationRequest req = new DraftApplicationRequest();
            ReflectionTestUtils.setField(req, "answers", Collections.emptyList());

            recruitmentService.saveDraft(1L, 1L, req);

            verify(applicantAnswerRepository).deleteByApplicantId(42L);
            verify(applicantAnswerRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-005 유효한 answers → 기존 삭제 후 새 답변 교체")
        void answersReplaced() {
            Applicant draft = createDraftApplicant(active, user);
            ApplicationQuestion q1 = createTextQuestion(1L, active, ApplicationQuestion.Category.COMMON, 1, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));
            when(applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(1L))
                    .thenReturn(List.of(q1));

            DraftApplicationRequest req = new DraftApplicationRequest();
            ReflectionTestUtils.setField(req, "answers",
                    List.of(buildTextAnswer(1L, "새 답변")));

            recruitmentService.saveDraft(1L, 1L, req);

            verify(applicantAnswerRepository).deleteByApplicantId(42L);
            verify(applicantAnswerRepository).save(any(ApplicantAnswer.class));
        }

        @Test
        @DisplayName("TC-006 알 수 없는 questionId → skip (예외 없음)")
        void unknownQuestionIdSkipped() {
            Applicant draft = createDraftApplicant(active, user);
            ApplicationQuestion q1 = createTextQuestion(1L, active, ApplicationQuestion.Category.COMMON, 1, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));
            when(applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(1L))
                    .thenReturn(List.of(q1));

            DraftApplicationRequest req = new DraftApplicationRequest();
            ReflectionTestUtils.setField(req, "answers",
                    List.of(buildTextAnswer(999L, "알 수 없는 질문"))); // valid IDs: [1]

            recruitmentService.saveDraft(1L, 1L, req);

            // 예외 없이 정상 완료, answers 저장 안 됨
            verify(applicantAnswerRepository).deleteByApplicantId(42L);
            verify(applicantAnswerRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-007 이미 SUBMITTED → APPLICATION_ALREADY_SUBMITTED")
        void alreadySubmitted() {
            Applicant submitted = createSubmittedApplicant(active, user);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(submitted));

            DraftApplicationRequest req = new DraftApplicationRequest();

            assertThatThrownBy(() -> recruitmentService.saveDraft(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.APPLICATION_ALREADY_SUBMITTED);
        }

        @Test
        @DisplayName("TC-008 모집 기간 외 → RECRUITMENT_CLOSED")
        void closed() {
            Recruitment expired = createExpiredRecruitment();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(expired));

            DraftApplicationRequest req = new DraftApplicationRequest();

            assertThatThrownBy(() -> recruitmentService.saveDraft(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_CLOSED);
        }
    }

    // ══════════════════════════════════════════════
    // REC-008: 내 지원서 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-008 내 지원서 조회")
    class GetMyApplication {

        private Recruitment active;
        private User user;

        @BeforeEach
        void setUp() {
            active = createActiveRecruitment();
            user = createUser(1L);
        }

        @Test
        @DisplayName("TC-001 DRAFT 지원서 → 200, null 필드 포함 반환")
        void draft() {
            Applicant draft = createDraftApplicant(active, user);
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));
            when(applicantAnswerRepository.findByApplicantIds(List.of(42L))).thenReturn(Collections.emptyList());

            MyApplicationResponse res = recruitmentService.getMyApplication(1L, 1L);

            assertThat(res.getStatus()).isEqualTo(Applicant.ApplicantStatus.DRAFT);
            assertThat(res.getPhone()).isNull();
        }

        @Test
        @DisplayName("TC-002 일부 필드 null인 DRAFT → null 포함 정상 반환")
        void draftWithNulls() {
            Applicant draft = createDraftApplicant(active, user);
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));
            when(applicantAnswerRepository.findByApplicantIds(any())).thenReturn(Collections.emptyList());

            MyApplicationResponse res = recruitmentService.getMyApplication(1L, 1L);

            assertThat(res).isNotNull();
            assertThat(res.getUniversity()).isNull();
        }

        @Test
        @DisplayName("TC-003 지원서 없음 → APPLICATION_NOT_FOUND")
        void noApplication() {
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getMyApplication(1L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-004 SUBMITTED 상태 → APPLICATION_ALREADY_SUBMITTED")
        void submitted() {
            Applicant submitted = createSubmittedApplicant(active, user);
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(submitted));

            assertThatThrownBy(() -> recruitmentService.getMyApplication(1L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.APPLICATION_ALREADY_SUBMITTED);
        }

        @Test
        @DisplayName("TC-005 공고 없음 → RECRUITMENT_NOT_FOUND (지원서 조회 전)")
        void noRecruitment() {
            when(recruitmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getMyApplication(1L, 999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);

            verify(applicantRepository, never()).findByRecruitmentIdAndUserId(any(), any());
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-001: 지원서 CSV 파일 생성
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-001 지원서 CSV 파일 생성")
    class DownloadApplications {

        @Test
        @DisplayName("TC-001 지원자 있는 공고 → 부문별 CSV 3개 S3 업로드")
        void uploadThreeCsvFiles() throws Exception {
            Recruitment r = createActiveRecruitment();
            User u = createUser(1L);
            Applicant eng = createSubmittedApplicant(r, u);

            when(recruitmentRepository.findByTerm(27)).thenReturn(Optional.of(r));
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackOrderBySubmittedAt(
                    eq(1L), eq(Track.ENGINEERING), any())).thenReturn(List.of(eng));
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackOrderBySubmittedAt(
                    eq(1L), eq(Track.ANALYSIS), any())).thenReturn(Collections.emptyList());
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackOrderBySubmittedAt(
                    eq(1L), eq(Track.VISUALIZATION), any())).thenReturn(Collections.emptyList());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(applicantAnswerRepository.findByApplicantIds(any())).thenReturn(Collections.emptyList());
            when(csvService.generateCsv(any(), any(), any())).thenReturn(new byte[0]);

            recruitmentService.downloadApplications(27);

            verify(s3Service, times(3)).uploadCsv(eq("test-bucket"), any(), any());
        }

        @Test
        @DisplayName("TC-002 지원자 없는 공고 → 헤더만 있는 빈 CSV 3개 (예외 없음)")
        void emptyApplicants() throws Exception {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findByTerm(27)).thenReturn(Optional.of(r));
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackOrderBySubmittedAt(any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(csvService.generateCsv(any(), any(), any())).thenReturn(new byte[0]);

            recruitmentService.downloadApplications(27);

            verify(s3Service, times(3)).uploadCsv(any(), any(), any());
        }

        @Test
        @DisplayName("TC-003 존재하지 않는 term → RECRUITMENT_NOT_FOUND")
        void notFound() {
            when(recruitmentRepository.findByTerm(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.downloadApplications(999))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-010: 지원서 전체 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-010 지원서 전체 삭제")
    class DeleteApplicants {

        @Test
        @DisplayName("TC-001 모집 마감 후 삭제 → answer 먼저, applicant 나중 삭제")
        void deleteInOrder() {
            Recruitment r = createExpiredRecruitment();
            when(recruitmentRepository.findById(2L)).thenReturn(Optional.of(r));

            recruitmentService.deleteApplicants(2L);

            var inOrder = inOrder(applicantAnswerRepository, applicantRepository);
            inOrder.verify(applicantAnswerRepository).deleteByRecruitmentId(2L);
            inOrder.verify(applicantRepository).deleteByRecruitmentId(2L);
        }

        @Test
        @DisplayName("TC-002 지원서 없어도 → 200 (예외 없음)")
        void emptyData() {
            Recruitment r = createExpiredRecruitment();
            when(recruitmentRepository.findById(2L)).thenReturn(Optional.of(r));

            recruitmentService.deleteApplicants(2L);

            verify(applicantAnswerRepository).deleteByRecruitmentId(2L);
        }

        @Test
        @DisplayName("TC-003 모집 진행 중 → RECRUITMENT_NOT_CLOSED")
        void recruitingNow() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> recruitmentService.deleteApplicants(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_CLOSED);
        }

        @Test
        @DisplayName("TC-004 존재하지 않는 공고 → RECRUITMENT_NOT_FOUND")
        void notFound() {
            when(recruitmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.deleteApplicants(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-011: 사전 알림 신청 전체 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-011 모든 모집 사전 알림 신청 조회")
    class GetAllSubscriptions {

        @Test
        @DisplayName("TC-001 데이터 있을 때 → created_at 내림차순 반환")
        void withData() {
            Subscription s1 = createSubscription(3L, "user3@example.com");
            Subscription s2 = createSubscription(2L, "user2@example.com");
            when(subscriptionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(s1, s2));

            List<SubscriptionResponse> result = recruitmentService.getAllSubscriptions();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getEmail()).isEqualTo("user3@example.com");
        }

        @Test
        @DisplayName("TC-002 데이터 없을 때 → 빈 배열 (예외 없음)")
        void noData() {
            when(subscriptionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());

            List<SubscriptionResponse> result = recruitmentService.getAllSubscriptions();

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-012: 사전 알림 신청 전체 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-012 모든 모집 사전 알림 신청 삭제")
    class DeleteAllSubscriptions {

        @Test
        @DisplayName("TC-001 데이터 있을 때 → deleteAll 호출")
        void withData() {
            recruitmentService.deleteAllSubscriptions();
            verify(subscriptionRepository).deleteAll();
        }

        @Test
        @DisplayName("TC-002 데이터 없을 때도 → 예외 없음")
        void noData() {
            doNothing().when(subscriptionRepository).deleteAll();
            recruitmentService.deleteAllSubscriptions();
            verify(subscriptionRepository).deleteAll();
        }
    }
}
