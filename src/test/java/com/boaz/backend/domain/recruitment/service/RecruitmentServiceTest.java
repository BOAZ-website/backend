package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.recruitment.dto.request.AnswerRequest;
import com.boaz.backend.domain.recruitment.dto.request.ApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.DraftApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.QuestionItemRequest;
import com.boaz.backend.domain.recruitment.dto.request.QuestionsCreateRequest;
import com.boaz.backend.domain.recruitment.dto.request.QuestionUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.request.RecruitmentCreateRequest;
import com.boaz.backend.domain.recruitment.dto.request.RecruitmentUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.request.SubscriptionRequest;
import org.openapitools.jackson.nullable.JsonNullable;
import java.util.concurrent.atomic.AtomicLong;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        ReflectionTestUtils.setField(recruitmentService, "clock", Clock.systemDefaultZone());
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

    private Recruitment createUpcomingRecruitment() {
        Recruitment r = Recruitment.create(28,
                LocalDateTime.now().plusDays(14), LocalDateTime.now().plusDays(30),
                "[]", null);
        ReflectionTestUtils.setField(r, "id", 3L);
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
                null, 500, null, orderNum, required);
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private ApplicationQuestion createTableQuestion(Long id, Recruitment r,
            ApplicationQuestion.Category category, int orderNum, boolean required) {
        ApplicationQuestion q = ApplicationQuestion.create(
                r, "label-t-" + id, category,
                ApplicationQuestion.Type.TABLE, "테이블질문 " + id,
                null, null, "{\"rows\":[\"A\"],\"columns\":[\"B\"]}", orderNum, required);
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private ApplicationQuestion createMultiTableQuestion(Long id, Recruitment r,
            ApplicationQuestion.Category category, int orderNum, boolean required) {
        ApplicationQuestion q = ApplicationQuestion.create(
                r, "label-mt-" + id, category,
                ApplicationQuestion.Type.TABLE, "복수선택질문 " + id,
                null, null,
                "{\"rows\":[\"1월 4일\",\"1월 5일\"],\"columns\":[\"12:00~14:00\",\"14:00~16:00\"],\"multiple\":true}",
                orderNum, required);
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
            when(recruitmentRepository.findCurrentOrUpcoming(any())).thenReturn(Optional.of(r));

            RecruitmentStatusResponse res = recruitmentService.getRecruitmentStatus();

            assertThat(res.getIsActive()).isTrue();
            assertThat(res.getTerm()).isEqualTo(27);
        }

        @Test
        @DisplayName("TC-002 마감된 공고만 존재 (endDate 경과) → isActive:false, term:null")
        void expiredRecruitment() {
            when(recruitmentRepository.findCurrentOrUpcoming(any())).thenReturn(Optional.empty());

            RecruitmentStatusResponse res = recruitmentService.getRecruitmentStatus();

            assertThat(res.getIsActive()).isFalse();
            assertThat(res.getTerm()).isNull();
        }

        @Test
        @DisplayName("TC-003 공고 데이터 없음 → isActive:false (예외 없음)")
        void noData() {
            when(recruitmentRepository.findCurrentOrUpcoming(any())).thenReturn(Optional.empty());

            RecruitmentStatusResponse res = recruitmentService.getRecruitmentStatus();

            assertThat(res.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("TC-004 start_date 정각 → isActive:true")
        void startDateBoundary() {
            LocalDateTime fixedNow = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
            Clock fixed = Clock.fixed(fixedNow.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
            ReflectionTestUtils.setField(recruitmentService, "clock", fixed);

            Recruitment r = Recruitment.create(27, fixedNow, fixedNow.plusDays(7), "[]", null);
            ReflectionTestUtils.setField(r, "id", 1L);
            when(recruitmentRepository.findCurrentOrUpcoming(any())).thenReturn(Optional.of(r));

            assertThat(recruitmentService.getRecruitmentStatus().getIsActive()).isTrue();
        }

        @Test
        @DisplayName("TC-005 end_date 1초 초과 → isActive:false")
        void endDatePast() {
            when(recruitmentRepository.findCurrentOrUpcoming(any())).thenReturn(Optional.empty());

            assertThat(recruitmentService.getRecruitmentStatus().getIsActive()).isFalse();
        }

        @Test
        @DisplayName("TC-006 예정 공고 존재 (start_date 미래) → isActive:false, term 반환")
        void upcomingRecruitment() {
            Recruitment r = createUpcomingRecruitment();
            when(recruitmentRepository.findCurrentOrUpcoming(any())).thenReturn(Optional.of(r));

            RecruitmentStatusResponse res = recruitmentService.getRecruitmentStatus();

            assertThat(res.getIsActive()).isFalse();
            assertThat(res.getTerm()).isEqualTo(28);
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

        @Test
        @DisplayName("TC-004 예정 공고 조회 → isActive:false + 날짜/일정 필드 반환 (null 아님)")
        void upcomingRecruitment() {
            Recruitment r = createUpcomingRecruitment();
            when(recruitmentRepository.findByTerm(28)).thenReturn(Optional.of(r));

            RecruitmentResponse res = recruitmentService.getRecruitment(28);

            assertThat(res.getIsActive()).isFalse();
            assertThat(res.getTerm()).isEqualTo(28);
            assertThat(res.getStartDate()).isNotNull();
            assertThat(res.getEndDate()).isNotNull();
            assertThat(res.getSchedule()).isNotNull();
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
        @DisplayName("TC-006a 전화번호 형식 오류(하이픈 포함) → INVALID_PHONE_FORMAT")
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
        @DisplayName("TC-006b 전화번호 형식 오류(9자리 이하) → INVALID_PHONE_FORMAT")
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
        @DisplayName("TC-011a TEXT 질문(is_required=false)에 객체 답변 → INVALID_ANSWER_TYPE")
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
                            buildJsonAnswer(2L, objNode)));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ANSWER_TYPE);
        }

        @Test
        @DisplayName("TC-011b TABLE 질문(is_required=false)에 텍스트 답변 → INVALID_ANSWER_TYPE")
        void tableQuestionWithTextAnswer() {
            ApplicationQuestion tableQ = createTableQuestion(2L, activeRecruitment,
                    ApplicationQuestion.Category.COMMON, 2, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, tableQ));

            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "정상답변"),
                            buildTextAnswer(2L, "텍스트 답변"))); // TABLE 질문에 텍스트

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ANSWER_TYPE);
        }

        @Test
        @DisplayName("TC-011d 단일선택 TABLE에 배열 값 → INVALID_ANSWER_TYPE")
        void singleTableWithArrayValue() throws Exception {
            ApplicationQuestion tableQ = createTableQuestion(2L, activeRecruitment,
                    ApplicationQuestion.Category.COMMON, 2, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, tableQ));

            JsonNode arrayVal = objectMapper.readTree("{\"A\":[\"B\"]}");
            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "정상"),
                            buildJsonAnswer(2L, arrayVal)));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ANSWER_TYPE);
        }

        @Test
        @DisplayName("TC-012 복수선택 TABLE 정상 제출 → 배열로 저장")
        void multiTableNormalSubmit() throws Exception {
            ApplicationQuestion multiQ = createMultiTableQuestion(2L, activeRecruitment,
                    ApplicationQuestion.Category.COMMON, 2, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, multiQ));
            when(applicantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            JsonNode answer = objectMapper.readTree(
                    "{\"1월 4일\":[\"12:00~14:00\"],\"1월 5일\":[]}");
            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "정상"),
                            buildJsonAnswer(2L, answer)));

            recruitmentService.submitApplication(1L, 1L, req);

            verify(applicantAnswerRepository, org.mockito.Mockito.atLeastOnce()).save(
                    org.mockito.ArgumentMatchers.argThat(a ->
                            a.getAnswerJson() != null && a.getAnswerJson().contains("12:00~14:00")));
        }

        @Test
        @DisplayName("TC-013 복수선택 TABLE 필수 질문 + 모든 행 빈 배열 → ANSWER_REQUIRED")
        void multiTableAllEmptyRequired() throws Exception {
            ApplicationQuestion multiQ = createMultiTableQuestion(2L, activeRecruitment,
                    ApplicationQuestion.Category.COMMON, 2, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, multiQ));

            JsonNode answer = objectMapper.readTree("{\"1월 4일\":[],\"1월 5일\":[]}");
            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "정상"),
                            buildJsonAnswer(2L, answer)));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ANSWER_REQUIRED);
        }

        @Test
        @DisplayName("TC-014 복수선택 TABLE 값이 배열 아님(문자열) → INVALID_ANSWER_TYPE")
        void multiTableValueNotArray() throws Exception {
            ApplicationQuestion multiQ = createMultiTableQuestion(2L, activeRecruitment,
                    ApplicationQuestion.Category.COMMON, 2, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, multiQ));

            JsonNode answer = objectMapper.readTree("{\"1월 4일\":\"12:00~14:00\"}");
            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "정상"),
                            buildJsonAnswer(2L, answer)));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ANSWER_TYPE);
        }

        @Test
        @DisplayName("TC-015 복수선택 TABLE 배열 원소가 string 아님 → INVALID_ANSWER_TYPE")
        void multiTableElementNotString() throws Exception {
            ApplicationQuestion multiQ = createMultiTableQuestion(2L, activeRecruitment,
                    ApplicationQuestion.Category.COMMON, 2, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, multiQ));

            JsonNode answer = objectMapper.readTree("{\"1월 4일\":[123]}");
            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "정상"),
                            buildJsonAnswer(2L, answer)));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ANSWER_TYPE);
        }

        @Test
        @DisplayName("TC-016 복수선택 TABLE 중복 원소 → dedupe 후 저장")
        void multiTableDedupeOnSave() throws Exception {
            ApplicationQuestion multiQ = createMultiTableQuestion(2L, activeRecruitment,
                    ApplicationQuestion.Category.COMMON, 2, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, multiQ));
            when(applicantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // 같은 시간 중복
            JsonNode answer = objectMapper.readTree(
                    "{\"1월 4일\":[\"12:00~14:00\",\"12:00~14:00\",\"14:00~16:00\"]}");
            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "정상"),
                            buildJsonAnswer(2L, answer)));

            recruitmentService.submitApplication(1L, 1L, req);

            org.mockito.ArgumentCaptor<ApplicantAnswer> captor =
                    org.mockito.ArgumentCaptor.forClass(ApplicantAnswer.class);
            verify(applicantAnswerRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

            String savedJson = captor.getAllValues().stream()
                    .filter(a -> a.getAnswerJson() != null)
                    .map(ApplicantAnswer::getAnswerJson)
                    .findFirst().orElseThrow();
            // "12:00~14:00"이 한 번만 등장해야 함
            assertThat(savedJson.split("12:00~14:00", -1).length - 1).isEqualTo(1);
            // 중복 제거 후 "14:00~16:00"은 그대로 살아있어야 함
            assertThat(savedJson).contains("14:00~16:00");
        }

        @Test
        @DisplayName("TC-013b 복수선택 필수 질문 + 빈 객체 {} → ANSWER_REQUIRED")
        void multiTableEmptyObjectRequired() {
            ApplicationQuestion multiQ = createMultiTableQuestion(2L, activeRecruitment,
                    ApplicationQuestion.Category.COMMON, 2, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1, multiQ));

            ApplicationRequest req = buildApplicationRequest(
                    List.of(buildTextAnswer(1L, "정상"),
                            buildJsonAnswer(2L, objectMapper.createObjectNode())));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ANSWER_REQUIRED);
        }

        @Test
        @DisplayName("TC-011c track=ALL → INVALID_TRACK_SELECTION")
        void submitWithTrackAll() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(activeRecruitment));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변")));
            ReflectionTestUtils.setField(req, "track", Track.ALL);

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
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

        @Test
        @DisplayName("TC-009 복수선택 TABLE 배열 답변 → 정상 임시저장")
        void multiTableArrayDraftSaved() throws Exception {
            Applicant draft = createDraftApplicant(active, user);
            ApplicationQuestion multiQ = createMultiTableQuestion(2L, active,
                    ApplicationQuestion.Category.COMMON, 2, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));
            when(applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(1L))
                    .thenReturn(List.of(multiQ));

            DraftApplicationRequest req = new DraftApplicationRequest();
            ReflectionTestUtils.setField(req, "answers",
                    List.of(buildJsonAnswer(2L, objectMapper.readTree(
                            "{\"1월 4일\":[\"12:00~14:00\"]}"))));

            recruitmentService.saveDraft(1L, 1L, req);

            verify(applicantAnswerRepository).save(org.mockito.ArgumentMatchers.argThat(
                    a -> a.getAnswerJson() != null && a.getAnswerJson().contains("12:00~14:00")));
        }

        @Test
        @DisplayName("TC-010 복수선택 TABLE에 문자열 값 → INVALID_ANSWER_TYPE")
        void multiTableStringValueInDraft() throws Exception {
            Applicant draft = createDraftApplicant(active, user);
            ApplicationQuestion multiQ = createMultiTableQuestion(2L, active,
                    ApplicationQuestion.Category.COMMON, 2, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));
            when(applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(1L))
                    .thenReturn(List.of(multiQ));

            DraftApplicationRequest req = new DraftApplicationRequest();
            ReflectionTestUtils.setField(req, "answers",
                    List.of(buildJsonAnswer(2L, objectMapper.readTree("{\"1월 4일\":\"12:00~14:00\"}"))));

            assertThatThrownBy(() -> recruitmentService.saveDraft(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ANSWER_TYPE);
        }

        @Test
        @DisplayName("TC-011 단일선택 TABLE에 배열 값 → INVALID_ANSWER_TYPE")
        void singleTableArrayValueInDraft() throws Exception {
            Applicant draft = createDraftApplicant(active, user);
            ApplicationQuestion tableQ = createTableQuestion(2L, active,
                    ApplicationQuestion.Category.COMMON, 2, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(active));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.of(draft));
            when(applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(1L))
                    .thenReturn(List.of(tableQ));

            DraftApplicationRequest req = new DraftApplicationRequest();
            ReflectionTestUtils.setField(req, "answers",
                    List.of(buildJsonAnswer(2L, objectMapper.readTree("{\"A\":[\"B\"]}"))));

            assertThatThrownBy(() -> recruitmentService.saveDraft(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ANSWER_TYPE);
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
    // 질문 생성/수정 — validateQuestionType
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("질문 생성 — metadata 검증")
    class QuestionMetadataValidation {

        private com.boaz.backend.domain.recruitment.dto.request.QuestionItemRequest buildTableItem(
                String metadataJson) throws Exception {
            com.boaz.backend.domain.recruitment.dto.request.QuestionItemRequest item =
                    new com.boaz.backend.domain.recruitment.dto.request.QuestionItemRequest();
            ReflectionTestUtils.setField(item, "label", "MULTI1");
            ReflectionTestUtils.setField(item, "category", ApplicationQuestion.Category.COMMON);
            ReflectionTestUtils.setField(item, "type", ApplicationQuestion.Type.TABLE);
            ReflectionTestUtils.setField(item, "content", "질문");
            ReflectionTestUtils.setField(item, "orderNum", 1);
            ReflectionTestUtils.setField(item, "isRequired", true);
            ReflectionTestUtils.setField(item, "metadata", objectMapper.readTree(metadataJson));
            return item;
        }

        private com.boaz.backend.domain.recruitment.dto.request.QuestionsCreateRequest buildCreateReq(
                com.boaz.backend.domain.recruitment.dto.request.QuestionItemRequest item) {
            com.boaz.backend.domain.recruitment.dto.request.QuestionsCreateRequest req =
                    new com.boaz.backend.domain.recruitment.dto.request.QuestionsCreateRequest();
            ReflectionTestUtils.setField(req, "recruitmentId", 1L);
            ReflectionTestUtils.setField(req, "questions", List.of(item));
            return req;
        }

        @Test
        @DisplayName("TABLE metadata의 multiple이 boolean string이면 INVALID_INPUT_VALUE")
        void multipleAsStringRejected() throws Exception {
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(createActiveRecruitment()));

            assertThatThrownBy(() -> recruitmentService.createQuestions(
                    buildCreateReq(buildTableItem(
                            "{\"rows\":[\"A\"],\"columns\":[\"B\"],\"multiple\":\"true\"}"))))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("TABLE metadata의 multiple이 boolean이면 정상 생성")
        void multipleAsBooleanAllowed() throws Exception {
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(createActiveRecruitment()));
            when(applicationQuestionRepository.save(any())).thenAnswer(inv -> {
                ApplicationQuestion q = inv.getArgument(0);
                ReflectionTestUtils.setField(q, "id", 1L);
                return q;
            });

            recruitmentService.createQuestions(
                    buildCreateReq(buildTableItem(
                            "{\"rows\":[\"A\"],\"columns\":[\"B\"],\"multiple\":true}")));

            verify(applicationQuestionRepository).save(any(ApplicationQuestion.class));
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
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackAndDecision(
                    eq(1L), eq(Track.ENGINEERING), any(), any())).thenReturn(List.of(eng));
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackAndDecision(
                    eq(1L), eq(Track.ANALYSIS), any(), any())).thenReturn(Collections.emptyList());
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackAndDecision(
                    eq(1L), eq(Track.VISUALIZATION), any(), any())).thenReturn(Collections.emptyList());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(applicantAnswerRepository.findByApplicantIds(any())).thenReturn(Collections.emptyList());
            when(csvService.generateCsv(any(), any(), any())).thenReturn(new byte[0]);

            recruitmentService.downloadApplications(27, DecisionFilter.ALL);

            verify(s3Service, times(3)).uploadCsv(eq("test-bucket"), any(), any());
        }

        @Test
        @DisplayName("TC-002 지원자 없는 공고 → 헤더만 있는 빈 CSV 3개 (예외 없음)")
        void emptyApplicants() throws Exception {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findByTerm(27)).thenReturn(Optional.of(r));
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackAndDecision(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(csvService.generateCsv(any(), any(), any())).thenReturn(new byte[0]);

            recruitmentService.downloadApplications(27, DecisionFilter.ALL);

            verify(s3Service, times(3)).uploadCsv(any(), any(), any());
        }

        @Test
        @DisplayName("TC-003 존재하지 않는 term → RECRUITMENT_NOT_FOUND")
        void notFound() {
            when(recruitmentRepository.findByTerm(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.downloadApplications(999, DecisionFilter.ALL))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-012 decision=PASS → 합격 필터로 조회하고 S3 키에 PASS 표기")
        void passFilter() throws Exception {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findByTerm(27)).thenReturn(Optional.of(r));
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackAndDecision(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(csvService.generateCsv(any(), any(), any())).thenReturn(new byte[0]);

            recruitmentService.downloadApplications(27, DecisionFilter.PASS);

            // 조회 시 final_decision=PASS 필터가 전달됨
            verify(applicantRepository, times(3)).findSubmittedByRecruitmentIdAndTrackAndDecision(
                    any(), any(), any(), eq(EvaluationDecision.PASS));
            // S3 키에 PASS 표기 (부문별 3건)
            org.mockito.ArgumentCaptor<String> keyCaptor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            verify(s3Service, times(3)).uploadCsv(any(), keyCaptor.capture(), any());
            assertThat(keyCaptor.getAllValues()).allMatch(k -> k.contains("_PASS_"));
        }

        @Test
        @DisplayName("TC-013 decision=ALL → final_decision 필터 null(전체) 전달")
        void allFilterPassesNull() throws Exception {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findByTerm(27)).thenReturn(Optional.of(r));
            when(applicantRepository.findSubmittedByRecruitmentIdAndTrackAndDecision(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(csvService.generateCsv(any(), any(), any())).thenReturn(new byte[0]);

            recruitmentService.downloadApplications(27, DecisionFilter.ALL);

            // ALL → null 필터(전체)로 조회
            verify(applicantRepository, times(3)).findSubmittedByRecruitmentIdAndTrackAndDecision(
                    any(), any(), any(), isNull());
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

    // ══════════════════════════════════════════════
    // REC-008: 모집 마감 경계 (#163 마지막 초 처리)
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-008 모집 마감 경계 (마지막 초 처리)")
    class DeadlineBoundary {

        private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
        private final LocalDateTime endDate = LocalDateTime.of(2026, 6, 24, 23, 59, 59);

        // 모집 기간 판정에 쓰이는 현재 시각을 고정한다.
        private void fixClockAt(LocalDateTime at) {
            Clock fixed = Clock.fixed(at.atZone(ZONE).toInstant(), ZONE);
            ReflectionTestUtils.setField(recruitmentService, "clock", fixed);
        }

        private Recruitment recruitmentEndingAt(LocalDateTime end) {
            Recruitment r = Recruitment.create(27,
                    end.minusDays(16), end, "[]", "https://example.com/brochure.pdf");
            ReflectionTestUtils.setField(r, "id", 1L);
            return r;
        }

        private void stubHappyPathSubmit(Recruitment r) {
            User user = createUser(1L);
            ApplicationQuestion q1 = createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicantRepository.findByRecruitmentIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
            when(applicationQuestionRepository.findByRecruitmentIdAndCategories(any(), any(), any()))
                    .thenReturn(List.of(q1));
            when(applicantRepository.save(any())).thenAnswer(inv -> {
                Applicant a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 42L);
                return a;
            });
        }

        @Test
        @DisplayName("TC-001 마감 초의 끝자락(23:59:59.999) 제출 → 접수 (튕기지 않음)")
        void submitAtLastSecondMillis() {
            fixClockAt(endDate.plusNanos(999_000_000)); // 2026-06-24 23:59:59.999
            stubHappyPathSubmit(recruitmentEndingAt(endDate));

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변내용")));
            ApplicationResponse res = recruitmentService.submitApplication(1L, 1L, req);

            assertThat(res.getApplicantId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("TC-002 마감 정각(23:59:59.000) 제출 → 접수")
        void submitExactlyAtEnd() {
            fixClockAt(endDate); // 2026-06-24 23:59:59.000
            stubHappyPathSubmit(recruitmentEndingAt(endDate));

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변내용")));
            ApplicationResponse res = recruitmentService.submitApplication(1L, 1L, req);

            assertThat(res.getApplicantId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("TC-003 다음날 자정(06-25 00:00:00) 제출 → RECRUITMENT_CLOSED")
        void submitAtNextMidnight() {
            fixClockAt(endDate.toLocalDate().plusDays(1).atStartOfDay()); // 2026-06-25 00:00:00
            User user = createUser(1L);
            Recruitment r = recruitmentEndingAt(endDate);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));

            ApplicationRequest req = buildApplicationRequest(List.of(buildTextAnswer(1L, "답변내용")));

            assertThatThrownBy(() -> recruitmentService.submitApplication(1L, 1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_CLOSED);
        }
    }

    // ──────────────────────────────────────────────
    // Admin CRUD 테스트 헬퍼
    // ──────────────────────────────────────────────

    private RecruitmentCreateRequest buildCreateRecruitmentReq(
            Integer term, LocalDateTime start, LocalDateTime end, String brochure) {
        RecruitmentCreateRequest req = new RecruitmentCreateRequest();
        ReflectionTestUtils.setField(req, "term", term);
        ReflectionTestUtils.setField(req, "startDate", start);
        ReflectionTestUtils.setField(req, "endDate", end);
        ReflectionTestUtils.setField(req, "schedule", objectMapper.createArrayNode());
        ReflectionTestUtils.setField(req, "brochureUrl", brochure);
        return req;
    }

    private QuestionItemRequest buildQuestionItem(String label, ApplicationQuestion.Category category,
            ApplicationQuestion.Type type, Integer limitLength, JsonNode metadata, int orderNum) {
        QuestionItemRequest item = new QuestionItemRequest();
        ReflectionTestUtils.setField(item, "label", label);
        ReflectionTestUtils.setField(item, "category", category);
        ReflectionTestUtils.setField(item, "type", type);
        ReflectionTestUtils.setField(item, "content", "질문 " + label);
        ReflectionTestUtils.setField(item, "limitLength", limitLength);
        ReflectionTestUtils.setField(item, "metadata", metadata);
        ReflectionTestUtils.setField(item, "orderNum", orderNum);
        ReflectionTestUtils.setField(item, "isRequired", true);
        return item;
    }

    private QuestionsCreateRequest buildQuestionsCreateReq(Long recruitmentId, QuestionItemRequest... items) {
        QuestionsCreateRequest req = new QuestionsCreateRequest();
        ReflectionTestUtils.setField(req, "recruitmentId", recruitmentId);
        ReflectionTestUtils.setField(req, "questions", List.of(items));
        return req;
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-002: 모든 모집 공고 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-002 모든 모집 공고 조회")
    class GetAllRecruitments {

        @Test
        @DisplayName("TC-001 공고 여러 건 → term 내림차순 목록 반환")
        void termDescList() {
            Recruitment r27 = createActiveRecruitment();   // term 27
            Recruitment r26 = createExpiredRecruitment();  // term 26
            when(recruitmentRepository.findAllByOrderByTermDesc()).thenReturn(List.of(r27, r26));

            List<RecruitmentResponse> result = recruitmentService.getAllRecruitments();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTerm()).isEqualTo(27);
        }

        @Test
        @DisplayName("TC-002 마감/예정 공고 포함 → is_active 무관 전체 필드 반환")
        void inactiveStillFullFields() {
            Recruitment upcoming = createUpcomingRecruitment(); // term 28, start 미래
            Recruitment expired = createExpiredRecruitment();   // term 26, end 과거
            when(recruitmentRepository.findAllByOrderByTermDesc()).thenReturn(List.of(upcoming, expired));

            List<RecruitmentResponse> result = recruitmentService.getAllRecruitments();

            RecruitmentResponse expiredRes = result.stream()
                    .filter(r -> r.getTerm() == 26).findFirst().orElseThrow();
            assertThat(expiredRes.getIsActive()).isFalse();
            assertThat(expiredRes.getStartDate()).isNotNull();
            assertThat(expiredRes.getEndDate()).isNotNull();

            RecruitmentResponse upcomingRes = result.stream()
                    .filter(r -> r.getTerm() == 28).findFirst().orElseThrow();
            assertThat(upcomingRes.getIsActive()).isFalse();
            assertThat(upcomingRes.getStartDate()).isNotNull();
        }

        @Test
        @DisplayName("TC-003 공고 없음 → 빈 배열 (404 아님)")
        void empty() {
            when(recruitmentRepository.findAllByOrderByTermDesc()).thenReturn(Collections.emptyList());

            assertThat(recruitmentService.getAllRecruitments()).isEmpty();
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-003: 모집 공고 등록
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-003 모집 공고 등록")
    class CreateRecruitment {

        @Test
        @DisplayName("TC-001 신규 기수 등록 → recruitment_id 반환")
        void createSuccess() {
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(15);
            RecruitmentCreateRequest req = buildCreateRecruitmentReq(28, start, end, "https://e.com/b.pdf");
            when(recruitmentRepository.existsByTerm(28)).thenReturn(false);
            when(recruitmentRepository.save(any())).thenAnswer(inv -> {
                Recruitment r = inv.getArgument(0);
                ReflectionTestUtils.setField(r, "id", 13L);
                return r;
            });

            RecruitmentIdResponse res = recruitmentService.createRecruitment(req);

            assertThat(res.getRecruitmentId()).isEqualTo(13L);
            verify(recruitmentRepository).save(any(Recruitment.class));
        }

        @Test
        @DisplayName("TC-002 brochure_url 미입력 → null 저장")
        void nullBrochure() {
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(15);
            RecruitmentCreateRequest req = buildCreateRecruitmentReq(28, start, end, null);
            when(recruitmentRepository.existsByTerm(28)).thenReturn(false);
            when(recruitmentRepository.save(any())).thenAnswer(inv -> {
                Recruitment r = inv.getArgument(0);
                ReflectionTestUtils.setField(r, "id", 13L);
                return r;
            });

            recruitmentService.createRecruitment(req);

            org.mockito.ArgumentCaptor<Recruitment> captor =
                    org.mockito.ArgumentCaptor.forClass(Recruitment.class);
            verify(recruitmentRepository).save(captor.capture());
            assertThat(captor.getValue().getBrochureUrl()).isNull();
        }

        @Test
        @DisplayName("TC-003 이미 존재하는 기수 → DUPLICATE_TERM")
        void duplicateTerm() {
            RecruitmentCreateRequest req = buildCreateRecruitmentReq(
                    27, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(15), null);
            when(recruitmentRepository.existsByTerm(27)).thenReturn(true);

            assertThatThrownBy(() -> recruitmentService.createRecruitment(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_TERM);
            verify(recruitmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-004 end_date == start_date → INVALID_INPUT_VALUE")
        void endNotAfterStart() {
            LocalDateTime same = LocalDateTime.now().plusDays(1);
            RecruitmentCreateRequest req = buildCreateRecruitmentReq(28, same, same, null);
            when(recruitmentRepository.existsByTerm(28)).thenReturn(false);

            assertThatThrownBy(() -> recruitmentService.createRecruitment(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            verify(recruitmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-004b end_date < start_date → INVALID_INPUT_VALUE")
        void endBeforeStart() {
            LocalDateTime start = LocalDateTime.now().plusDays(15);
            LocalDateTime end = LocalDateTime.now().plusDays(1); // end < start
            RecruitmentCreateRequest req = buildCreateRecruitmentReq(28, start, end, null);
            when(recruitmentRepository.existsByTerm(28)).thenReturn(false);

            assertThatThrownBy(() -> recruitmentService.createRecruitment(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            verify(recruitmentRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-004: 모집 공고 수정
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-004 모집 공고 수정")
    class UpdateRecruitment {

        @Test
        @DisplayName("TC-001 start_date만 수정 → 해당 필드만 변경")
        void updateStartOnly() {
            Recruitment r = createActiveRecruitment(); // term 27, id 1
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            LocalDateTime newStart = LocalDateTime.now().minusDays(2);
            RecruitmentUpdateRequest req = new RecruitmentUpdateRequest();
            ReflectionTestUtils.setField(req, "startDate", newStart);

            RecruitmentIdResponse res = recruitmentService.updateRecruitment(1L, req);

            assertThat(res.getRecruitmentId()).isEqualTo(1L);
            assertThat(r.getStartDate()).isEqualTo(newStart);
            assertThat(r.getTerm()).isEqualTo(27); // 미포함 필드 유지
        }

        @Test
        @DisplayName("TC-002 schedule 수정 → 배열 전체 교체")
        void replaceSchedule() throws Exception {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            var schedule = objectMapper.createArrayNode();
            schedule.add(objectMapper.createObjectNode().put("step", "서류"));
            RecruitmentUpdateRequest req = new RecruitmentUpdateRequest();
            ReflectionTestUtils.setField(req, "schedule", schedule);

            recruitmentService.updateRecruitment(1L, req);

            assertThat(r.getSchedule()).isEqualTo(objectMapper.writeValueAsString(schedule));
        }

        @Test
        @DisplayName("TC-003 brochure_url null 명시 전송 → 삭제")
        void deleteBrochure() {
            Recruitment r = createActiveRecruitment(); // brochure 존재
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            RecruitmentUpdateRequest req = new RecruitmentUpdateRequest();
            ReflectionTestUtils.setField(req, "brochureUrl", JsonNullable.of(null));

            recruitmentService.updateRecruitment(1L, req);

            assertThat(r.getBrochureUrl()).isNull();
        }

        @Test
        @DisplayName("TC-003b brochure_url 미전송(undefined) → 기존 값 유지")
        void brochureUndefinedKept() {
            Recruitment r = createActiveRecruitment(); // brochure 존재
            String original = r.getBrochureUrl();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            RecruitmentUpdateRequest req = new RecruitmentUpdateRequest();
            ReflectionTestUtils.setField(req, "startDate", LocalDateTime.now().minusDays(2));
            // brochureUrl 은 JsonNullable.undefined() 기본값 → 전송 안 함

            recruitmentService.updateRecruitment(1L, req);

            assertThat(r.getBrochureUrl()).isEqualTo(original);
        }

        @Test
        @DisplayName("TC-004 존재하지 않는 공고 → RECRUITMENT_NOT_FOUND")
        void notFound() {
            when(recruitmentRepository.findById(999L)).thenReturn(Optional.empty());
            RecruitmentUpdateRequest req = new RecruitmentUpdateRequest();

            assertThatThrownBy(() -> recruitmentService.updateRecruitment(999L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-005 다른 공고와 term 중복 → DUPLICATE_TERM")
        void duplicateTerm() {
            Recruitment r = createActiveRecruitment(); // id 1
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(recruitmentRepository.existsByTermAndIdNot(26, 1L)).thenReturn(true);
            RecruitmentUpdateRequest req = new RecruitmentUpdateRequest();
            ReflectionTestUtils.setField(req, "term", 26);

            assertThatThrownBy(() -> recruitmentService.updateRecruitment(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_TERM);
        }

        @Test
        @DisplayName("TC-006 end_date만 수정했는데 기존 start_date보다 이전 → INVALID_INPUT_VALUE")
        void endBeforeExistingStart() {
            Recruitment r = createActiveRecruitment(); // start = now-1d
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            RecruitmentUpdateRequest req = new RecruitmentUpdateRequest();
            ReflectionTestUtils.setField(req, "endDate", LocalDateTime.now().minusDays(2));

            assertThatThrownBy(() -> recruitmentService.updateRecruitment(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-005: 모집 공고 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-005 모집 공고 삭제")
    class DeleteRecruitment {

        @Test
        @DisplayName("TC-001 연관 데이터 없는 공고 → 삭제")
        void deleteSuccess() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicantRepository.existsByRecruitmentId(1L)).thenReturn(false);
            when(applicationQuestionRepository.existsByRecruitmentId(1L)).thenReturn(false);

            recruitmentService.deleteRecruitment(1L);

            verify(recruitmentRepository).delete(r);
        }

        @Test
        @DisplayName("TC-002 존재하지 않는 공고 → RECRUITMENT_NOT_FOUND")
        void notFound() {
            when(recruitmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.deleteRecruitment(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-003 연관 지원자 존재 → RECRUITMENT_HAS_REFERENCES")
        void hasApplicants() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicantRepository.existsByRecruitmentId(1L)).thenReturn(true);

            assertThatThrownBy(() -> recruitmentService.deleteRecruitment(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_HAS_REFERENCES);
            verify(recruitmentRepository, never()).delete(any());
        }

        @Test
        @DisplayName("TC-004 연관 질문 존재 → RECRUITMENT_HAS_REFERENCES")
        void hasQuestions() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicantRepository.existsByRecruitmentId(1L)).thenReturn(false);
            when(applicationQuestionRepository.existsByRecruitmentId(1L)).thenReturn(true);

            assertThatThrownBy(() -> recruitmentService.deleteRecruitment(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_HAS_REFERENCES);
            verify(recruitmentRepository, never()).delete(any());
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-006: 지원서 질문 등록
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-006 지원서 질문 등록")
    class CreateQuestions {

        @Test
        @DisplayName("TC-001 TEXT+TABLE 다건 등록 → ids 반환")
        void createMixedSuccess() throws Exception {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.existsByRecruitmentIdAndLabel(eq(1L), any())).thenReturn(false);
            when(applicationQuestionRepository.existsByRecruitmentIdAndCategoryAndOrderNum(eq(1L), any(), any()))
                    .thenReturn(false);
            AtomicLong seq = new AtomicLong(100);
            when(applicationQuestionRepository.save(any())).thenAnswer(inv -> {
                ApplicationQuestion q = inv.getArgument(0);
                ReflectionTestUtils.setField(q, "id", seq.getAndIncrement());
                return q;
            });

            JsonNode metadata = objectMapper.readTree("{\"rows\":[\"A\"],\"columns\":[\"B\"]}");
            QuestionsCreateRequest req = buildQuestionsCreateReq(1L,
                    buildQuestionItem("C1", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, 500, null, 1),
                    buildQuestionItem("E1", ApplicationQuestion.Category.ENGINEERING,
                            ApplicationQuestion.Type.TABLE, null, metadata, 1));

            QuestionIdsResponse res = recruitmentService.createQuestions(req);

            assertThat(res.getIds()).hasSize(2);
            verify(applicationQuestionRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("TC-002 다른 category면 동일 order_num 허용")
        void sameOrderDifferentCategory() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.existsByRecruitmentIdAndLabel(eq(1L), any())).thenReturn(false);
            when(applicationQuestionRepository.existsByRecruitmentIdAndCategoryAndOrderNum(eq(1L), any(), any()))
                    .thenReturn(false);
            when(applicationQuestionRepository.save(any())).thenAnswer(inv -> {
                ApplicationQuestion q = inv.getArgument(0);
                ReflectionTestUtils.setField(q, "id", 1L);
                return q;
            });

            QuestionsCreateRequest req = buildQuestionsCreateReq(1L,
                    buildQuestionItem("C1", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, 500, null, 1),
                    buildQuestionItem("E1", ApplicationQuestion.Category.ENGINEERING,
                            ApplicationQuestion.Type.TEXT, 500, null, 1));

            QuestionIdsResponse res = recruitmentService.createQuestions(req);

            assertThat(res.getIds()).hasSize(2);
        }

        @Test
        @DisplayName("TC-003 존재하지 않는 공고 → RECRUITMENT_NOT_FOUND")
        void notFound() {
            when(recruitmentRepository.findById(999L)).thenReturn(Optional.empty());
            QuestionsCreateRequest req = buildQuestionsCreateReq(999L,
                    buildQuestionItem("C1", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, 500, null, 1));

            assertThatThrownBy(() -> recruitmentService.createQuestions(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-004 요청 내 label 중복 → DUPLICATE_QUESTION_LABEL")
        void inRequestDuplicateLabel() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            QuestionsCreateRequest req = buildQuestionsCreateReq(1L,
                    buildQuestionItem("C1", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, 500, null, 1),
                    buildQuestionItem("C1", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, 500, null, 2));

            assertThatThrownBy(() -> recruitmentService.createQuestions(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_QUESTION_LABEL);
            verify(applicationQuestionRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-005 요청 내 동일 category order_num 중복 → DUPLICATE_QUESTION_ORDER")
        void inRequestDuplicateOrder() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            QuestionsCreateRequest req = buildQuestionsCreateReq(1L,
                    buildQuestionItem("C1", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, 500, null, 1),
                    buildQuestionItem("C2", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, 500, null, 1));

            assertThatThrownBy(() -> recruitmentService.createQuestions(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_QUESTION_ORDER);
        }

        @Test
        @DisplayName("TC-006 DB에 동일 label 존재 → DUPLICATE_QUESTION_LABEL")
        void dbDuplicateLabel() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.existsByRecruitmentIdAndLabel(1L, "C1")).thenReturn(true);
            QuestionsCreateRequest req = buildQuestionsCreateReq(1L,
                    buildQuestionItem("C1", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, 500, null, 1));

            assertThatThrownBy(() -> recruitmentService.createQuestions(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_QUESTION_LABEL);
            verify(applicationQuestionRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-006b DB에 동일 category order_num 존재 → DUPLICATE_QUESTION_ORDER")
        void dbDuplicateOrder() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.existsByRecruitmentIdAndLabel(1L, "C1")).thenReturn(false);
            when(applicationQuestionRepository.existsByRecruitmentIdAndCategoryAndOrderNum(
                    1L, ApplicationQuestion.Category.COMMON, 1)).thenReturn(true);
            QuestionsCreateRequest req = buildQuestionsCreateReq(1L,
                    buildQuestionItem("C1", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, 500, null, 1));

            assertThatThrownBy(() -> recruitmentService.createQuestions(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_QUESTION_ORDER);
            verify(applicationQuestionRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-007 TEXT인데 limit_length 누락 → MISSING_PARAMETER")
        void textMissingLimitLength() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            QuestionsCreateRequest req = buildQuestionsCreateReq(1L,
                    buildQuestionItem("C1", ApplicationQuestion.Category.COMMON,
                            ApplicationQuestion.Type.TEXT, null, null, 1));

            assertThatThrownBy(() -> recruitmentService.createQuestions(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MISSING_PARAMETER);
        }

        @Test
        @DisplayName("TC-007b TABLE인데 metadata 누락 → MISSING_PARAMETER")
        void tableMissingMetadata() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            QuestionsCreateRequest req = buildQuestionsCreateReq(1L,
                    buildQuestionItem("T1", ApplicationQuestion.Category.ENGINEERING,
                            ApplicationQuestion.Type.TABLE, null, null, 1));

            assertThatThrownBy(() -> recruitmentService.createQuestions(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MISSING_PARAMETER);
            verify(applicationQuestionRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-007: 지원서 질문 목록 조회 (어드민)
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-007 지원서 질문 목록 조회 (어드민)")
    class GetAdminQuestions {

        @Test
        @DisplayName("TC-001 질문 존재 → order_num 오름차순 반환")
        void orderAscList() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(1L))
                    .thenReturn(List.of(
                            createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true),
                            createTextQuestion(2L, r, ApplicationQuestion.Category.ENGINEERING, 10, true)));

            List<QuestionResponse> result = recruitmentService.getAdminQuestions(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getOrderNum()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-002 마감/예정 공고도 조회 가능 (is_active 무관)")
        void inactiveStillReturns() {
            Recruitment r = createExpiredRecruitment(); // id 2
            when(recruitmentRepository.findById(2L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(2L))
                    .thenReturn(List.of(createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true)));

            List<QuestionResponse> result = recruitmentService.getAdminQuestions(2L);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("TC-003 {Track} 플레이스홀더 미치환 확인")
        void trackPlaceholderNotReplaced() {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q = ApplicationQuestion.create(r, "E1",
                    ApplicationQuestion.Category.ENGINEERING, ApplicationQuestion.Type.TEXT,
                    "{Track} 관련 경험", null, 500, null, 1, true);
            ReflectionTestUtils.setField(q, "id", 5L);
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(1L))
                    .thenReturn(List.of(q));

            List<QuestionResponse> result = recruitmentService.getAdminQuestions(1L);

            assertThat(result.get(0).getContent()).isEqualTo("{Track} 관련 경험");
        }

        @Test
        @DisplayName("TC-004 질문 없음 → 빈 배열 (QUESTIONS_NOT_FOUND 아님)")
        void emptyList() {
            Recruitment r = createActiveRecruitment();
            when(recruitmentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(1L))
                    .thenReturn(Collections.emptyList());

            assertThat(recruitmentService.getAdminQuestions(1L)).isEmpty();
        }

        @Test
        @DisplayName("TC-005 존재하지 않는 공고 → RECRUITMENT_NOT_FOUND")
        void notFound() {
            when(recruitmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getAdminQuestions(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-008: 지원서 질문 수정
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-008 지원서 질문 수정")
    class UpdateQuestion {

        @Test
        @DisplayName("TC-001 content만 수정 → 해당 필드만 변경")
        void updateContentOnly() {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q = createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true);
            when(applicationQuestionRepository.findById(1L)).thenReturn(Optional.of(q));
            QuestionUpdateRequest req = new QuestionUpdateRequest();
            ReflectionTestUtils.setField(req, "content", "수정된 질문 내용");

            QuestionIdResponse res = recruitmentService.updateQuestion(1L, req);

            assertThat(res.getQuestionId()).isEqualTo(1L);
            assertThat(q.getContent()).isEqualTo("수정된 질문 내용");
        }

        @Test
        @DisplayName("TC-002 TEXT→TABLE 타입 변경 (metadata 동반, limit_length 정리)")
        void changeTypeToTable() throws Exception {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q = createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true);
            when(applicationQuestionRepository.findById(1L)).thenReturn(Optional.of(q));
            JsonNode metadata = objectMapper.readTree("{\"rows\":[\"A\"],\"columns\":[\"B\"]}");
            QuestionUpdateRequest req = new QuestionUpdateRequest();
            ReflectionTestUtils.setField(req, "type", ApplicationQuestion.Type.TABLE);
            ReflectionTestUtils.setField(req, "metadata", JsonNullable.of(metadata));

            recruitmentService.updateQuestion(1L, req);

            assertThat(q.getType()).isEqualTo(ApplicationQuestion.Type.TABLE);
            assertThat(q.getMetadata()).isNotNull();
            assertThat(q.getLimitLength()).isNull();
        }

        @Test
        @DisplayName("TC-003 TABLE→TEXT 변경인데 limit_length 누락 → MISSING_PARAMETER")
        void changeTypeToTextMissingLimit() {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q = createTableQuestion(1L, r, ApplicationQuestion.Category.ENGINEERING, 1, true);
            when(applicationQuestionRepository.findById(1L)).thenReturn(Optional.of(q));
            QuestionUpdateRequest req = new QuestionUpdateRequest();
            ReflectionTestUtils.setField(req, "type", ApplicationQuestion.Type.TEXT);

            assertThatThrownBy(() -> recruitmentService.updateQuestion(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MISSING_PARAMETER);
        }

        @Test
        @DisplayName("TC-004 존재하지 않는 질문 → QUESTIONS_NOT_FOUND")
        void notFound() {
            when(applicationQuestionRepository.findById(999L)).thenReturn(Optional.empty());
            QuestionUpdateRequest req = new QuestionUpdateRequest();

            assertThatThrownBy(() -> recruitmentService.updateQuestion(999L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.QUESTIONS_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-005 같은 공고 내 label 중복 → DUPLICATE_QUESTION_LABEL")
        void duplicateLabel() {
            Recruitment r = createActiveRecruitment(); // id 1
            ApplicationQuestion q = createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true);
            when(applicationQuestionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(applicationQuestionRepository.existsByRecruitmentIdAndLabelAndIdNot(1L, "공통1", 1L))
                    .thenReturn(true);
            QuestionUpdateRequest req = new QuestionUpdateRequest();
            ReflectionTestUtils.setField(req, "label", "공통1");

            assertThatThrownBy(() -> recruitmentService.updateQuestion(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_QUESTION_LABEL);
        }

        @Test
        @DisplayName("TC-006 order_num 중복 (resolvedCategory 기준) → DUPLICATE_QUESTION_ORDER")
        void duplicateOrder() {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q = createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true);
            when(applicationQuestionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(applicationQuestionRepository.existsByRecruitmentIdAndCategoryAndOrderNumAndIdNot(
                    1L, ApplicationQuestion.Category.COMMON, 1, 1L)).thenReturn(true);
            QuestionUpdateRequest req = new QuestionUpdateRequest();
            ReflectionTestUtils.setField(req, "orderNum", 1);

            assertThatThrownBy(() -> recruitmentService.updateQuestion(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_QUESTION_ORDER);
        }

        @Test
        @DisplayName("TC-007 metadata null 명시 전송 → 삭제")
        void deleteMetadata() {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q = createTableQuestion(1L, r, ApplicationQuestion.Category.ENGINEERING, 1, true);
            when(applicationQuestionRepository.findById(1L)).thenReturn(Optional.of(q));
            QuestionUpdateRequest req = new QuestionUpdateRequest();
            ReflectionTestUtils.setField(req, "metadata", JsonNullable.of(null));

            recruitmentService.updateQuestion(1L, req);

            assertThat(q.getMetadata()).isNull();
        }

        @Test
        @DisplayName("TC-007b metadata 미전송(undefined) → 기존 값 유지")
        void metadataUndefinedKept() {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q = createTableQuestion(1L, r, ApplicationQuestion.Category.ENGINEERING, 1, true);
            String original = q.getMetadata();
            when(applicationQuestionRepository.findById(1L)).thenReturn(Optional.of(q));
            QuestionUpdateRequest req = new QuestionUpdateRequest();
            ReflectionTestUtils.setField(req, "content", "내용만 수정");
            // metadata, limitLength, description 모두 JsonNullable.undefined() 기본값 → 미전송

            recruitmentService.updateQuestion(1L, req);

            assertThat(q.getMetadata()).isEqualTo(original);
        }
    }

    // ══════════════════════════════════════════════
    // REC-ADMIN-009: 지원서 질문 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REC-ADMIN-009 지원서 질문 삭제")
    class DeleteQuestion {

        @Test
        @DisplayName("TC-001 답변 없는 질문 → 삭제")
        void deleteSuccess() {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q = createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true);
            when(applicationQuestionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(applicantAnswerRepository.existsByQuestionId(1L)).thenReturn(false);

            recruitmentService.deleteQuestion(1L);

            verify(applicationQuestionRepository).delete(q);
        }

        @Test
        @DisplayName("TC-002 존재하지 않는 질문 → QUESTIONS_NOT_FOUND")
        void notFound() {
            when(applicationQuestionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.deleteQuestion(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.QUESTIONS_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-003 참조 답변 존재 → QUESTION_HAS_ANSWERS")
        void hasAnswers() {
            Recruitment r = createActiveRecruitment();
            ApplicationQuestion q = createTextQuestion(1L, r, ApplicationQuestion.Category.COMMON, 1, true);
            when(applicationQuestionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(applicantAnswerRepository.existsByQuestionId(1L)).thenReturn(true);

            assertThatThrownBy(() -> recruitmentService.deleteQuestion(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.QUESTION_HAS_ANSWERS);
            verify(applicationQuestionRepository, never()).delete(any());
        }
    }
}
