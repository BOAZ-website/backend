package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.recruitment.dto.response.*;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.UserPrincipal;
import com.boaz.backend.support.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(
    value = RecruitmentController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class RecruitmentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RecruitmentService recruitmentService;

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    // ──────────────────────────────────────────────
    // REC-001: GET /api/v1/recruitment/status
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-001 GET /api/v1/recruitment/status")
    class GetStatus {

        @Test
        @DisplayName("TC-006 정상 조회 → 200 + is_active:true")
        void success() throws Exception {
            RecruitmentStatusResponse res = RecruitmentStatusResponse.of(true, 27);
            when(recruitmentService.getRecruitmentStatus()).thenReturn(res);

            mockMvc.perform(get("/api/v1/recruitment/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.is_active").value(true))
                    .andExpect(jsonPath("$.data.term").value(27));
        }

        @Test
        @DisplayName("TC-006 비모집 기간 → is_active:false (term 키 생략)")
        void inactive() throws Exception {
            RecruitmentStatusResponse res = RecruitmentStatusResponse.of(false, null);
            when(recruitmentService.getRecruitmentStatus()).thenReturn(res);

            mockMvc.perform(get("/api/v1/recruitment/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.is_active").value(false))
                    .andExpect(jsonPath("$.data.term").doesNotExist());
        }

        @Test
        @DisplayName("[Security] 인증 없이 접근 가능 (공개 API)")
        void publicApi() throws Exception {
            RecruitmentStatusResponse res = RecruitmentStatusResponse.of(false, null);
            when(recruitmentService.getRecruitmentStatus()).thenReturn(res);

            mockMvc.perform(get("/api/v1/recruitment/status"))
                    .andExpect(status().isOk());
        }
    }

    // ──────────────────────────────────────────────
    // REC-002: GET /api/v1/recruitment/{term}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-002 GET /api/v1/recruitment/{term}")
    class GetRecruitment {

        @Test
        @DisplayName("TC-005 정상 조회 → 200 + 공고 응답")
        void success() throws Exception {
            when(recruitmentService.getRecruitment(27)).thenReturn(mockRecruitmentResponse());

            mockMvc.perform(get("/api/v1/recruitment/27"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.term").value(27));
        }

        @Test
        @DisplayName("TC-004 term 파라미터 타입 오류 → 400 INVALID_PARAMETER_TYPE")
        void invalidTermType() throws Exception {
            mockMvc.perform(get("/api/v1/recruitment/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_PARAMETER_TYPE"));
        }

        @Test
        @DisplayName("EX-001 존재하지 않는 term → 404 RECRUITMENT_NOT_FOUND")
        void notFound() throws Exception {
            when(recruitmentService.getRecruitment(999))
                    .thenThrow(new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/recruitment/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_NOT_FOUND"));
        }
    }

    // ──────────────────────────────────────────────
    // REC-003: GET /api/v1/recruitment/questions
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-003 GET /api/v1/recruitment/questions")
    class GetQuestions {

        @Test
        @DisplayName("TC-006 인증 없이 접근 가능 (공개 API)")
        void publicApi() throws Exception {
            when(recruitmentService.getQuestions(eq(1L), eq(Track.ENGINEERING)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/recruitment/questions")
                            .param("recruitmentId", "1")
                            .param("track", "ENGINEERING"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("EX-005 recruitmentId 누락 → 400 MISSING_PARAMETER")
        void missingParam() throws Exception {
            mockMvc.perform(get("/api/v1/recruitment/questions")
                            .param("track", "ENGINEERING"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("EX-004 track=ALL → 400 INVALID_TRACK_SELECTION")
        void trackAll() throws Exception {
            when(recruitmentService.getQuestions(any(), eq(Track.ALL)))
                    .thenThrow(new CustomException(ErrorCode.INVALID_TRACK_SELECTION));

            mockMvc.perform(get("/api/v1/recruitment/questions")
                            .param("recruitmentId", "1")
                            .param("track", "ALL"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_TRACK_SELECTION"));
        }

        @Test
        @DisplayName("EX-002 모집 기간 아님 → 400 RECRUITMENT_NOT_AVAILABLE")
        void notAvailable() throws Exception {
            when(recruitmentService.getQuestions(any(), any()))
                    .thenThrow(new CustomException(ErrorCode.RECRUITMENT_NOT_AVAILABLE));

            mockMvc.perform(get("/api/v1/recruitment/questions")
                            .param("recruitmentId", "1")
                            .param("track", "ENGINEERING"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_NOT_AVAILABLE"));
        }
    }

    // ──────────────────────────────────────────────
    // REC-004: POST /api/v1/recruitment/{id}/applications
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-004 POST /api/v1/recruitment/{id}/applications")
    class SubmitApplication {

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/recruitment/1/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }

        @Test
        @DisplayName("정상 제출 → 201 Created")
        void success() throws Exception {
            ApplicationResponse res = ApplicationResponse.of(42L, LocalDateTime.now());
            when(recruitmentService.submitApplication(eq(1L), eq(1L), any())).thenReturn(res);

            mockMvc.perform(post("/api/v1/recruitment/1/applications")
                            .with(authentication(userAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validApplicationJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.applicant_id").value(42));
        }

        @Test
        @DisplayName("EX-001 이미 SUBMITTED → 409 ALREADY_SUBMITTED")
        void alreadySubmitted() throws Exception {
            when(recruitmentService.submitApplication(any(), any(), any()))
                    .thenThrow(new CustomException(ErrorCode.ALREADY_SUBMITTED));

            mockMvc.perform(post("/api/v1/recruitment/1/applications")
                            .with(authentication(userAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validApplicationJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("ALREADY_SUBMITTED"));
        }

        @Test
        @DisplayName("EX-002 모집 기간 아님 → 409 RECRUITMENT_CLOSED")
        void closed() throws Exception {
            when(recruitmentService.submitApplication(any(), any(), any()))
                    .thenThrow(new CustomException(ErrorCode.RECRUITMENT_CLOSED));

            mockMvc.perform(post("/api/v1/recruitment/1/applications")
                            .with(authentication(userAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validApplicationJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_CLOSED"));
        }
    }

    // ──────────────────────────────────────────────
    // REC-005: POST /api/v1/recruitment/subscriptions
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-005 POST /api/v1/recruitment/subscriptions")
    class Subscribe {

        @Test
        @DisplayName("TC-005 정상 신청 → 201 Created")
        void success() throws Exception {
            when(recruitmentService.subscribe(any())).thenReturn(mockSubscriptionResponse());

            mockMvc.perform(post("/api/v1/recruitment/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"test@example.com\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.email").value("test@example.com"));
        }

        @Test
        @DisplayName("TC-006 이메일 미입력(@NotBlank) → 400 INVALID_INPUT_VALUE")
        void blankEmail() throws Exception {
            mockMvc.perform(post("/api/v1/recruitment/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("EX-001 중복 이메일 → 409 DUPLICATE_EMAIL")
        void duplicate() throws Exception {
            when(recruitmentService.subscribe(any()))
                    .thenThrow(new CustomException(ErrorCode.DUPLICATE_EMAIL));

            mockMvc.perform(post("/api/v1/recruitment/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"dup@example.com\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DUPLICATE_EMAIL"));
        }
    }

    // ──────────────────────────────────────────────
    // REC-006: GET /api/v1/recruitment/deadline
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-006 GET /api/v1/recruitment/deadline")
    class GetDeadline {

        @Test
        @DisplayName("TC-003 정상 조회 → 200 + 날짜 포맷 확인")
        void success() throws Exception {
            DeadlineResponse res = mockDeadlineResponse();
            when(recruitmentService.getDeadline()).thenReturn(res);

            mockMvc.perform(get("/api/v1/recruitment/deadline"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.recruitment_id").value(1))
                    .andExpect(jsonPath("$.data.deadline").exists());
        }

        @Test
        @DisplayName("EX-001 활성 공고 없음 → 404 RECRUITMENT_NOT_FOUND")
        void notFound() throws Exception {
            when(recruitmentService.getDeadline())
                    .thenThrow(new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/recruitment/deadline"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_NOT_FOUND"));
        }
    }

    // ──────────────────────────────────────────────
    // REC-007: PUT /api/v1/recruitment/{id}/applications/draft
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-007 PUT /api/v1/recruitment/{id}/applications/draft")
    class SaveDraft {

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(put("/api/v1/recruitment/1/applications/draft")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }

        @Test
        @DisplayName("정상 임시저장 → 200 + applicant_id")
        void success() throws Exception {
            DraftApplicationResponse res = DraftApplicationResponse.of(42L);
            when(recruitmentService.saveDraft(eq(1L), eq(1L), any())).thenReturn(res);

            mockMvc.perform(put("/api/v1/recruitment/1/applications/draft")
                            .with(authentication(userAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"홍길동\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.applicant_id").value(42));
        }

        @Test
        @DisplayName("EX-001 SUBMITTED 지원서 존재 → 403 APPLICATION_ALREADY_SUBMITTED")
        void alreadySubmitted() throws Exception {
            when(recruitmentService.saveDraft(any(), any(), any()))
                    .thenThrow(new CustomException(ErrorCode.APPLICATION_ALREADY_SUBMITTED));

            mockMvc.perform(put("/api/v1/recruitment/1/applications/draft")
                            .with(authentication(userAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("APPLICATION_ALREADY_SUBMITTED"));
        }
    }

    // ──────────────────────────────────────────────
    // REC-008: GET /api/v1/recruitment/{id}/applications/me
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-008 GET /api/v1/recruitment/{id}/applications/me")
    class GetMyApplication {

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/recruitment/1/applications/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }

        @Test
        @DisplayName("EX-002 SUBMITTED 상태 접근 → 403 APPLICATION_ALREADY_SUBMITTED")
        void submittedForbidden() throws Exception {
            when(recruitmentService.getMyApplication(any(), any()))
                    .thenThrow(new CustomException(ErrorCode.APPLICATION_ALREADY_SUBMITTED));

            mockMvc.perform(get("/api/v1/recruitment/1/applications/me")
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("APPLICATION_ALREADY_SUBMITTED"));
        }

        @Test
        @DisplayName("EX-001 지원서 없음 → 404 APPLICATION_NOT_FOUND")
        void notFound() throws Exception {
            when(recruitmentService.getMyApplication(any(), any()))
                    .thenThrow(new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

            mockMvc.perform(get("/api/v1/recruitment/1/applications/me")
                            .with(authentication(userAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("APPLICATION_NOT_FOUND"));
        }
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private String validApplicationJson() {
        return objectMapper.createObjectNode()
                .put("track", "ENGINEERING")
                .put("name", "홍길동")
                .put("email", "hong@example.com")
                .put("phone", "01012345678")
                .put("university", "한국대학교")
                .put("major", "컴퓨터공학")
                .put("last_semester", 6)
                .put("military_status", "COMPLETED_OR_EXEMPT")
                .put("birth_date", "2000-01-01")
                .put("graduation_date", "2026-02")
                .put("grad_school_plan", false)
                .set("answers", objectMapper.createArrayNode())
                .toString();
    }

    private SubscriptionResponse mockSubscriptionResponse() {
        com.boaz.backend.domain.recruitment.entity.Subscription sub =
                com.boaz.backend.domain.recruitment.entity.Subscription.builder()
                        .email("test@example.com").build();
        org.springframework.test.util.ReflectionTestUtils.setField(sub, "id", 1L);
        return SubscriptionResponse.from(sub);
    }

    private DeadlineResponse mockDeadlineResponse() {
        com.boaz.backend.domain.recruitment.entity.Recruitment r =
                com.boaz.backend.domain.recruitment.entity.Recruitment.create(
                        27, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7),
                        "[]", null);
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 1L);
        return DeadlineResponse.from(r);
    }

    private com.boaz.backend.domain.recruitment.dto.response.RecruitmentResponse mockRecruitmentResponse() {
        com.boaz.backend.domain.recruitment.entity.Recruitment r =
                com.boaz.backend.domain.recruitment.entity.Recruitment.create(
                        27, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                        "[]", null);
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 1L);
        return com.boaz.backend.domain.recruitment.dto.response.RecruitmentResponse.from(r, true);
    }
}
