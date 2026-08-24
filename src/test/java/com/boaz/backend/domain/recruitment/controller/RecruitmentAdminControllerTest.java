package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.recruitment.dto.response.QuestionIdResponse;
import com.boaz.backend.domain.recruitment.dto.response.QuestionIdsResponse;
import com.boaz.backend.domain.recruitment.dto.response.QuestionResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentIdResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentResponse;
import com.boaz.backend.domain.recruitment.dto.response.SubscriptionResponse;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.DecisionFilter;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion.Category;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion.Type;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    @MockitoBean RecruitmentService recruitmentService;

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
        @DisplayName("정상 요청(decision 미지정) → 200, 기본값 ALL 전달")
        void success() throws Exception {
            doNothing().when(recruitmentService).downloadApplications(eq(27), any());

            mockMvc.perform(post("/api/v1/admin/recruitment/applications/download")
                            .with(authentication(adminAuth()))
                            .param("term", "27"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));

            verify(recruitmentService).downloadApplications(27, DecisionFilter.ALL);
        }

        @Test
        @DisplayName("decision=PASS 지정 → 200, PASS 필터 전달")
        void successWithPassFilter() throws Exception {
            doNothing().when(recruitmentService).downloadApplications(eq(27), any());

            mockMvc.perform(post("/api/v1/admin/recruitment/applications/download")
                            .with(authentication(adminAuth()))
                            .param("term", "27")
                            .param("decision", "PASS"))
                    .andExpect(status().isOk());

            verify(recruitmentService).downloadApplications(27, DecisionFilter.PASS);
        }

        @Test
        @DisplayName("decision 값이 ENUM 범위 밖 → 400")
        void invalidDecision() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/applications/download")
                            .with(authentication(adminAuth()))
                            .param("term", "27")
                            .param("decision", "INVALID"))
                    .andExpect(status().isBadRequest());
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
                    .when(recruitmentService).downloadApplications(eq(999), any());

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
                    .when(recruitmentService).downloadApplications(eq(27), any());

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

    private RecruitmentResponse buildRecruitmentResponse(Long id, int term) {
        Recruitment r = Recruitment.create(term,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), "[]", null);
        ReflectionTestUtils.setField(r, "id", id);
        return RecruitmentResponse.from(r, LocalDateTime.now());
    }

    private QuestionResponse buildQuestionResponse(Long id, int orderNum) {
        ApplicationQuestion q = ApplicationQuestion.create(
                null, "L" + id, Category.COMMON, Type.TEXT, "content", null, 500, null, orderNum, true);
        ReflectionTestUtils.setField(q, "id", id);
        return QuestionResponse.from(q);
    }

    private String recruitmentCreateJson(boolean withTerm) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (withTerm) body.put("term", 28);
        body.put("start_date", "2026-08-01T00:00:00");
        body.put("end_date", "2026-08-15T23:59:59");
        body.put("schedule", List.of());
        body.put("brochure_url", "https://e.com/b.pdf");
        return objectMapper.writeValueAsString(body);
    }

    private String recruitmentCreateJsonMissing(String fieldToOmit) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("term", 28);
        body.put("start_date", "2026-08-01T00:00:00");
        body.put("end_date", "2026-08-15T23:59:59");
        body.put("schedule", List.of());
        body.put("brochure_url", "https://e.com/b.pdf");
        body.remove(fieldToOmit);
        return objectMapper.writeValueAsString(body);
    }

    private String questionsCreateJson(boolean withQuestions) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("recruitment_id", 1);
        if (withQuestions) {
            Map<String, Object> item = new HashMap<>();
            item.put("label", "공통1");
            item.put("category", "COMMON");
            item.put("type", "TEXT");
            item.put("content", "질문");
            item.put("limit_length", 500);
            item.put("order_num", 1);
            item.put("is_required", true);
            body.put("questions", List.of(item));
        } else {
            body.put("questions", List.of());
        }
        return objectMapper.writeValueAsString(body);
    }

    private String questionsCreateJsonMissing(String fieldToOmit) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("recruitment_id", 1);
        Map<String, Object> item = new HashMap<>();
        item.put("label", "공통1");
        item.put("category", "COMMON");
        item.put("type", "TEXT");
        item.put("content", "질문");
        item.put("limit_length", 500);
        item.put("order_num", 1);
        item.put("is_required", true);
        item.remove(fieldToOmit);
        body.put("questions", List.of(item));
        body.remove(fieldToOmit); // recruitment_id 케이스는 최상위에서 제거
        return objectMapper.writeValueAsString(body);
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-002: GET /api/v1/admin/recruitment
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-002 GET /api/v1/admin/recruitment")
    class GetAllRecruitments {

        @Test
        @DisplayName("TC-004 정상 조회 → 200 + 목록")
        void success() throws Exception {
            when(recruitmentService.getAllRecruitments())
                    .thenReturn(List.of(buildRecruitmentResponse(12L, 27), buildRecruitmentResponse(11L, 26)));

            mockMvc.perform(get("/api/v1/admin/recruitment")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].recruitment_id").value(12));
        }

        @Test
        @DisplayName("TC-005 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/admin/recruitment"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }

        @Test
        @DisplayName("TC-006 User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/recruitment")
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-003: POST /api/v1/admin/recruitment
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-003 POST /api/v1/admin/recruitment")
    class CreateRecruitment {

        @Test
        @DisplayName("TC-006 정상 등록 → 201 + recruitment_id")
        void success() throws Exception {
            when(recruitmentService.createRecruitment(any())).thenReturn(RecruitmentIdResponse.of(13L));

            mockMvc.perform(post("/api/v1/admin/recruitment")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recruitmentCreateJson(true)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.recruitment_id").value(13));
        }

        @Test
        @DisplayName("TC-005 필수 필드(term) 누락 → 400 INVALID_INPUT_VALUE (@Valid)")
        void missingField() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recruitmentCreateJson(false)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-005b 필수 필드(start_date) 누락 → 400 INVALID_INPUT_VALUE (@Valid)")
        void missingStartDate() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recruitmentCreateJsonMissing("start_date")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-005c 필수 필드(end_date) 누락 → 400 INVALID_INPUT_VALUE (@Valid)")
        void missingEndDate() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recruitmentCreateJsonMissing("end_date")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-005d 필수 필드(schedule) 누락 → 400 INVALID_INPUT_VALUE (@Valid)")
        void missingSchedule() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recruitmentCreateJsonMissing("schedule")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("EX-003 중복 기수 → 409 DUPLICATE_TERM")
        void duplicateTerm() throws Exception {
            when(recruitmentService.createRecruitment(any()))
                    .thenThrow(new CustomException(ErrorCode.DUPLICATE_TERM));

            mockMvc.perform(post("/api/v1/admin/recruitment")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recruitmentCreateJson(true)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DUPLICATE_TERM"));
        }

        @Test
        @DisplayName("TC-007 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recruitmentCreateJson(true)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment")
                            .with(authentication(userAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recruitmentCreateJson(true)))
                    .andExpect(status().isForbidden());
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-004: PATCH /api/v1/admin/recruitment/{id}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-004 PATCH /api/v1/admin/recruitment/{id}")
    class UpdateRecruitment {

        @Test
        @DisplayName("TC-007 정상 수정 → 200 + recruitment_id")
        void success() throws Exception {
            when(recruitmentService.updateRecruitment(eq(12L), any())).thenReturn(RecruitmentIdResponse.of(12L));

            mockMvc.perform(patch("/api/v1/admin/recruitment/12")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"start_date\":\"2026-08-01T00:00:00\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.recruitment_id").value(12));
        }

        @Test
        @DisplayName("EX-003 존재하지 않는 공고 → 404 RECRUITMENT_NOT_FOUND")
        void notFound() throws Exception {
            when(recruitmentService.updateRecruitment(eq(999L), any()))
                    .thenThrow(new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

            mockMvc.perform(patch("/api/v1/admin/recruitment/999")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"term\":28}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/recruitment/12")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/recruitment/12")
                            .with(authentication(userAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-005: DELETE /api/v1/admin/recruitment/{id}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-005 DELETE /api/v1/admin/recruitment/{id}")
    class DeleteRecruitment {

        @Test
        @DisplayName("TC-005 정상 삭제 → 200")
        void success() throws Exception {
            doNothing().when(recruitmentService).deleteRecruitment(12L);

            mockMvc.perform(delete("/api/v1/admin/recruitment/12")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("EX-001 연관 데이터 존재 → 400 RECRUITMENT_HAS_REFERENCES")
        void hasReferences() throws Exception {
            doThrow(new CustomException(ErrorCode.RECRUITMENT_HAS_REFERENCES))
                    .when(recruitmentService).deleteRecruitment(12L);

            mockMvc.perform(delete("/api/v1/admin/recruitment/12")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_HAS_REFERENCES"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/recruitment/12"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/recruitment/12")
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden());
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-006: POST /api/v1/admin/recruitment/questions
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-006 POST /api/v1/admin/recruitment/questions")
    class CreateQuestions {

        @Test
        @DisplayName("TC-001 정상 등록 → 201 + ids")
        void success() throws Exception {
            when(recruitmentService.createQuestions(any())).thenReturn(QuestionIdsResponse.of(List.of(1L, 2L)));

            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJson(true)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.ids.length()").value(2));
        }

        @Test
        @DisplayName("TC-009 questions 빈 배열 → 400 INVALID_INPUT_VALUE (@NotEmpty)")
        void emptyQuestions() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJson(false)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009b item의 order_num 0 → 400 INVALID_INPUT_VALUE (@Valid cascade @Positive)")
        void itemOrderNumNotPositive() throws Exception {
            Map<String, Object> item = new HashMap<>();
            item.put("label", "공통1");
            item.put("category", "COMMON");
            item.put("type", "TEXT");
            item.put("content", "질문");
            item.put("limit_length", 500);
            item.put("order_num", 0); // @Positive 위반
            item.put("is_required", true);
            Map<String, Object> body = new HashMap<>();
            body.put("recruitment_id", 1);
            body.put("questions", List.of(item));

            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009c item의 label 누락 → 400 INVALID_INPUT_VALUE (@Valid cascade @NotBlank)")
        void itemLabelMissing() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJsonMissing("label")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009d item의 category 누락 → 400 INVALID_INPUT_VALUE (@Valid cascade @NotNull)")
        void itemCategoryMissing() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJsonMissing("category")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009e item의 type 누락 → 400 INVALID_INPUT_VALUE (@Valid cascade @NotNull)")
        void itemTypeMissing() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJsonMissing("type")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009f item의 content 누락 → 400 INVALID_INPUT_VALUE (@Valid cascade @NotBlank)")
        void itemContentMissing() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJsonMissing("content")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009g item의 is_required 누락 → 400 INVALID_INPUT_VALUE (@Valid cascade @NotNull)")
        void itemIsRequiredMissing() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJsonMissing("is_required")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009h recruitment_id 누락 → 400 INVALID_INPUT_VALUE (@NotNull)")
        void recruitmentIdMissing() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJsonMissing("recruitment_id")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("EX-005 존재하지 않는 공고 → 404 RECRUITMENT_NOT_FOUND")
        void notFound() throws Exception {
            when(recruitmentService.createQuestions(any()))
                    .thenThrow(new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJson(true)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJson(true)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/recruitment/questions")
                            .with(authentication(userAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionsCreateJson(true)))
                    .andExpect(status().isForbidden());
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-007: GET /api/v1/admin/recruitment/{recruitmentId}/questions
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-007 GET /api/v1/admin/recruitment/{recruitmentId}/questions")
    class GetAdminQuestions {

        @Test
        @DisplayName("TC-006 정상 조회 → 200 + 목록")
        void success() throws Exception {
            when(recruitmentService.getAdminQuestions(12L))
                    .thenReturn(List.of(buildQuestionResponse(1L, 1), buildQuestionResponse(2L, 10)));

            mockMvc.perform(get("/api/v1/admin/recruitment/12/questions")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].order_num").value(1));
        }

        @Test
        @DisplayName("EX-001 존재하지 않는 공고 → 404 RECRUITMENT_NOT_FOUND")
        void notFound() throws Exception {
            when(recruitmentService.getAdminQuestions(999L))
                    .thenThrow(new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/admin/recruitment/999/questions")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("RECRUITMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/admin/recruitment/12/questions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/recruitment/12/questions")
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden());
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-008: PATCH /api/v1/admin/recruitment/questions/{questionId}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-008 PATCH /api/v1/admin/recruitment/questions/{questionId}")
    class UpdateQuestion {

        @Test
        @DisplayName("TC-008 정상 수정 → 200 + question_id")
        void success() throws Exception {
            when(recruitmentService.updateQuestion(eq(1L), any())).thenReturn(QuestionIdResponse.of(1L));

            mockMvc.perform(patch("/api/v1/admin/recruitment/questions/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"수정된 내용\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.question_id").value(1));
        }

        @Test
        @DisplayName("EX-003 존재하지 않는 질문 → 404 QUESTIONS_NOT_FOUND")
        void notFound() throws Exception {
            when(recruitmentService.updateQuestion(eq(999L), any()))
                    .thenThrow(new CustomException(ErrorCode.QUESTIONS_NOT_FOUND));

            mockMvc.perform(patch("/api/v1/admin/recruitment/questions/999")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"x\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("QUESTIONS_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/recruitment/questions/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/recruitment/questions/1")
                            .with(authentication(userAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ──────────────────────────────────────────────
    // REC-ADMIN-009: DELETE /api/v1/admin/recruitment/questions/{questionId}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REC-ADMIN-009 DELETE /api/v1/admin/recruitment/questions/{questionId}")
    class DeleteQuestion {

        @Test
        @DisplayName("TC-004 정상 삭제 → 200")
        void success() throws Exception {
            doNothing().when(recruitmentService).deleteQuestion(1L);

            mockMvc.perform(delete("/api/v1/admin/recruitment/questions/1")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("EX-001 참조 답변 존재 → 400 QUESTION_HAS_ANSWERS")
        void hasAnswers() throws Exception {
            doThrow(new CustomException(ErrorCode.QUESTION_HAS_ANSWERS))
                    .when(recruitmentService).deleteQuestion(1L);

            mockMvc.perform(delete("/api/v1/admin/recruitment/questions/1")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("QUESTION_HAS_ANSWERS"));
        }

        @Test
        @DisplayName("EX-002 존재하지 않는 질문 → 404 QUESTIONS_NOT_FOUND")
        void notFound() throws Exception {
            doThrow(new CustomException(ErrorCode.QUESTIONS_NOT_FOUND))
                    .when(recruitmentService).deleteQuestion(999L);

            mockMvc.perform(delete("/api/v1/admin/recruitment/questions/999")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("QUESTIONS_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/recruitment/questions/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403")
        void forbidden() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/recruitment/questions/1")
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden());
        }
    }
}
