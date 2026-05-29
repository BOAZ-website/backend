package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.recruitment.dto.response.SubscriptionResponse;
import com.boaz.backend.domain.recruitment.entity.Subscription;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(
    value = RecruitmentAdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class RecruitmentAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RecruitmentService recruitmentService;

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-001: POST /api/v1/admin/recruitment/applications/download
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-001 POST /api/v1/admin/recruitment/applications/download")
    class DownloadApplications {

        @Test
        @DisplayName("정상 요청 → 200")
        void success() throws Exception {
            doNothing().when(recruitmentService).downloadApplications(27);

            mockMvc.perform(post("/api/v1/admin/recruitment/applications/download")
                            .with(authentication(adminAuth()))
                            .param("term", "27"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("EX-004 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/applications/download")
                            .param("term", "27"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }

        @Test
        @DisplayName("EX-004 User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/applications/download")
                            .with(authentication(userAuth()))
                            .param("term", "27"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("EX-001 존재하지 않는 term → 404 RECRUITMENT_NOT_FOUND")
        void notFound() throws Exception {
            doThrow(new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND))
                    .when(recruitmentService).downloadApplications(999);

            mockMvc.perform(post("/api/v1/admin/recruitment/applications/download")
                            .with(authentication(adminAuth()))
                            .param("term", "999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("EX-002 S3 업로드 실패 → 500 S3_UPLOAD_FAILED")
        void s3Failed() throws Exception {
            doThrow(new CustomException(ErrorCode.S3_UPLOAD_FAILED))
                    .when(recruitmentService).downloadApplications(27);

            mockMvc.perform(post("/api/v1/admin/recruitment/applications/download")
                            .with(authentication(adminAuth()))
                            .param("term", "27"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error_code").value("S3_UPLOAD_FAILED"));
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-010: DELETE /api/v1/admin/recruitment/{id}/applicants
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-010 DELETE /api/v1/admin/recruitment/{id}/applicants")
    class DeleteApplicants {

        @Test
        @DisplayName("TC-001 정상 삭제 → 200")
        void success() throws Exception {
            doNothing().when(recruitmentService).deleteApplicants(1L);

            mockMvc.perform(delete("/api/v1/admin/recruitment/1/applicants")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("TC-005 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/recruitment/1/applicants"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("EX-004 User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/recruitment/1/applicants")
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EX-001 모집 진행 중 → 400 RECRUITMENT_NOT_CLOSED")
        void notClosed() throws Exception {
            doThrow(new CustomException(ErrorCode.RECRUITMENT_NOT_CLOSED))
                    .when(recruitmentService).deleteApplicants(1L);

            mockMvc.perform(delete("/api/v1/admin/recruitment/1/applicants")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_NOT_CLOSED"));
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-011: GET /api/v1/admin/recruitment/subscriptions
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-011 GET /api/v1/admin/recruitment/subscriptions")
    class GetSubscriptions {

        @Test
        @DisplayName("TC-001 데이터 있을 때 → 200 + 목록 반환")
        void withData() throws Exception {
            List<SubscriptionResponse> list = List.of(buildSubscriptionResponse(2L, "b@example.com"),
                    buildSubscriptionResponse(1L, "a@example.com"));
            when(recruitmentService.getAllSubscriptions()).thenReturn(list);

            mockMvc.perform(get("/api/v1/admin/recruitment/subscriptions")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("TC-002 데이터 없을 때 → 200 + 빈 배열")
        void empty() throws Exception {
            when(recruitmentService.getAllSubscriptions()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/admin/recruitment/subscriptions")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/admin/recruitment/subscriptions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/recruitment/subscriptions")
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden());
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-012: DELETE /api/v1/admin/recruitment/subscriptions
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-012 DELETE /api/v1/admin/recruitment/subscriptions")
    class DeleteAllSubscriptions {

        @Test
        @DisplayName("TC-003 정상 삭제 → 200")
        void success() throws Exception {
            doNothing().when(recruitmentService).deleteAllSubscriptions();

            mockMvc.perform(delete("/api/v1/admin/recruitment/subscriptions")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/recruitment/subscriptions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/recruitment/subscriptions")
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden());
        }
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private SubscriptionResponse buildSubscriptionResponse(Long id, String email) {
        Subscription s = Subscription.builder().email(email).build();
        ReflectionTestUtils.setField(s, "id", id);
        return SubscriptionResponse.from(s);
    }
}
