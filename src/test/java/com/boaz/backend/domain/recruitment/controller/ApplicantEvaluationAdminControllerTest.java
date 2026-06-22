package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.recruitment.dto.response.ApplicantEvaluatorsResponse;
import com.boaz.backend.domain.recruitment.dto.response.MyEvaluationResponse;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantEval;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.AdminUserDetails;
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
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(
        value = ApplicantEvaluationAdminController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class ApplicantEvaluationAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean RecruitmentService recruitmentService;

    private UsernamePasswordAuthenticationToken adminAuth() {
        Admin admin = Admin.builder()
                .username("rep").password("p").role(Admin.Role.SUPER).name("대표")
                .track(Track.ENGINEERING).term(27).teamName(Admin.TeamName.대표진).createdBy(null)
                .build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        return new UsernamePasswordAuthenticationToken(
                new AdminUserDetails(admin), null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MyEvaluationResponse myEval() {
        ApplicantEval e = mock(ApplicantEval.class);
        Applicant a = mock(Applicant.class);
        when(a.getId()).thenReturn(101L);
        when(e.getId()).thenReturn(9L);
        when(e.getApplicant()).thenReturn(a);
        when(e.getDecision()).thenReturn(EvaluationDecision.PASS);
        when(e.getScore()).thenReturn(10);
        when(e.getMemo()).thenReturn("great");
        return MyEvaluationResponse.from(e);
    }

    // ── 전체 지원서 조회 ──────────────────────────────────
    @Nested
    @DisplayName("GET /{recruitmentId}/applicants")
    class GetApplicants {

        @Test
        @DisplayName("[정상] 200 + data 배열")
        void success() throws Exception {
            given(recruitmentService.getApplicants(3L)).willReturn(List.of());

            mockMvc.perform(get("/api/v1/admin/recruitment/3/applicants").with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("[Security] 미인증 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/admin/recruitment/3/applicants"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] User 권한 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/recruitment/3/applicants").with(authentication(userAuth())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 공고 → 404")
        void notFound() throws Exception {
            given(recruitmentService.getApplicants(999L))
                    .willThrow(new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/admin/recruitment/999/applicants").with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_NOT_FOUND"));
        }
    }

    // ── 전체 지원서 및 평가 조회 ──────────────────────────
    @Nested
    @DisplayName("GET /{recruitmentId}/applicants/evaluations")
    class GetApplicantEvaluations {

        @Test
        @DisplayName("[정상] 200 + data 배열")
        void success() throws Exception {
            given(recruitmentService.getApplicantEvaluations(3L)).willReturn(List.of());

            mockMvc.perform(get("/api/v1/admin/recruitment/3/applicants/evaluations").with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ── 최종 평가 수정 ────────────────────────────────────
    @Nested
    @DisplayName("PATCH /applicants/{applicantId}/final-decision")
    class UpdateFinalDecision {

        @Test
        @DisplayName("[정상] 200")
        void success() throws Exception {
            given(recruitmentService.updateFinalDecision(eq(101L), any(), any())).willReturn(null);

            mockMvc.perform(patch("/api/v1/admin/recruitment/applicants/101/final-decision")
                            .with(authentication(adminAuth()))
                            .contentType("application/json")
                            .content("{\"final_decision\":\"PASS\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("[검증] final_decision 누락 → 400")
        void missingField() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/recruitment/applicants/101/final-decision")
                            .with(authentication(adminAuth()))
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("[권한] 대표진 아님(서비스 throw) → 403")
        void notRepresentative() throws Exception {
            given(recruitmentService.updateFinalDecision(eq(101L), any(), any()))
                    .willThrow(new CustomException(ErrorCode.ACCESS_DENIED));

            mockMvc.perform(patch("/api/v1/admin/recruitment/applicants/101/final-decision")
                            .with(authentication(adminAuth()))
                            .contentType("application/json")
                            .content("{\"final_decision\":\"PASS\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        }
    }

    // ── 지원서별 평가 조회 ────────────────────────────────
    @Nested
    @DisplayName("GET /applicants/{applicantId}/evaluations")
    class GetApplicantEvaluators {

        @Test
        @DisplayName("[정상] 200 + applicant_id + evaluations")
        void success() throws Exception {
            given(recruitmentService.getApplicantEvaluators(101L))
                    .willReturn(ApplicantEvaluatorsResponse.of(101L, List.of()));

            mockMvc.perform(get("/api/v1/admin/recruitment/applicants/101/evaluations").with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.applicant_id").value(101))
                    .andExpect(jsonPath("$.data.evaluations").isArray());
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → 404")
        void notFound() throws Exception {
            given(recruitmentService.getApplicantEvaluators(999L))
                    .willThrow(new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

            mockMvc.perform(get("/api/v1/admin/recruitment/applicants/999/evaluations").with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("APPLICATION_NOT_FOUND"));
        }
    }

    // ── 개인 평가 조회 ────────────────────────────────────
    @Nested
    @DisplayName("GET /applicants/{applicantId}/evaluations/me")
    class GetMyEvaluation {

        @Test
        @DisplayName("[정상] 평가 있음 → 200 + data")
        void found() throws Exception {
            MyEvaluationResponse res = myEval();
            given(recruitmentService.getMyEvaluation(eq(101L), any())).willReturn(res);

            mockMvc.perform(get("/api/v1/admin/recruitment/applicants/101/evaluations/me").with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.evaluation_id").value(9))
                    .andExpect(jsonPath("$.data.decision").value("PASS"));
        }

        @Test
        @DisplayName("[정상] 평가 없음 → 200 + data null")
        void none() throws Exception {
            given(recruitmentService.getMyEvaluation(eq(101L), any())).willReturn(null);

            mockMvc.perform(get("/api/v1/admin/recruitment/applicants/101/evaluations/me").with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    // ── 개인 평가 저장 ────────────────────────────────────
    @Nested
    @DisplayName("PUT /applicants/{applicantId}/evaluations/me")
    class SaveMyEvaluation {

        @Test
        @DisplayName("[정상] 200")
        void success() throws Exception {
            MyEvaluationResponse res = myEval();
            given(recruitmentService.saveMyEvaluation(eq(101L), any(), any())).willReturn(res);

            mockMvc.perform(put("/api/v1/admin/recruitment/applicants/101/evaluations/me")
                            .with(authentication(adminAuth()))
                            .contentType("application/json")
                            .content("{\"decision\":\"PASS\",\"score\":10,\"memo\":\"great\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.decision").value("PASS"));
        }

        @Test
        @DisplayName("[검증] decision 누락 → 400")
        void missingDecision() throws Exception {
            mockMvc.perform(put("/api/v1/admin/recruitment/applicants/101/evaluations/me")
                            .with(authentication(adminAuth()))
                            .contentType("application/json")
                            .content("{\"score\":5}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("[검증] score 범위 초과(11) → 400")
        void scoreOutOfRange() throws Exception {
            mockMvc.perform(put("/api/v1/admin/recruitment/applicants/101/evaluations/me")
                            .with(authentication(adminAuth()))
                            .contentType("application/json")
                            .content("{\"decision\":\"PASS\",\"score\":11}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("[권한] 타 부문(서비스 throw) → 403")
        void trackMismatch() throws Exception {
            given(recruitmentService.saveMyEvaluation(eq(101L), any(), any()))
                    .willThrow(new CustomException(ErrorCode.ACCESS_DENIED));

            mockMvc.perform(put("/api/v1/admin/recruitment/applicants/101/evaluations/me")
                            .with(authentication(adminAuth()))
                            .contentType("application/json")
                            .content("{\"decision\":\"PASS\",\"score\":10}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        }
    }
}
