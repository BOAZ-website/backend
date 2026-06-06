package com.boaz.backend.domain.faq.controller;

import com.boaz.backend.domain.faq.dto.response.FaqResponse;
import com.boaz.backend.domain.faq.entity.Faq;
import com.boaz.backend.domain.faq.service.FaqService;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(
        value = FaqAdminController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class FaqAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean FaqService faqService;

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    private FaqResponse makeFaqResponse(Long id, Faq.Category category, int orderNum) {
        Faq faq = Faq.create("질문", "답변", category, orderNum);
        ReflectionTestUtils.setField(faq, "id", id);
        return FaqResponse.from(faq);
    }

    private String createBody(String question, String answer, String category, int orderNum) {
        return objectMapper.createObjectNode()
                .put("question", question)
                .put("answer", answer)
                .put("category", category)
                .put("order_num", orderNum)
                .toString();
    }

    // ──────────────────────────────────────────────
    // FAQ-002: POST /api/v1/admin/faqs
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("FAQ-002 POST /api/v1/admin/faqs")
    class CreateFaq {

        @Test
        @DisplayName("TC-003 FAQ 등록 성공 → 201 Created")
        void success() throws Exception {
            when(faqService.createFaq(any())).thenReturn(makeFaqResponse(1L, Faq.Category.RECRUITMENT, 1));

            mockMvc.perform(post("/api/v1/admin/faqs")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("지원 방법?", "홈페이지를 통해 지원", "RECRUITMENT", 1)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.order_num").value(1))
                    .andExpect(jsonPath("$.data.category").value("RECRUITMENT"));
        }

        @Test
        @DisplayName("TC-004 동일 카테고리 내 orderNum 중복 → 409 DUPLICATE_ORDER_NUM")
        void duplicateOrderNum() throws Exception {
            when(faqService.createFaq(any()))
                    .thenThrow(new CustomException(ErrorCode.DUPLICATE_ORDER_NUM));

            mockMvc.perform(post("/api/v1/admin/faqs")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("다른 질문", "다른 답변", "RECRUITMENT", 1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DUPLICATE_ORDER_NUM"));
        }

        @Test
        @DisplayName("필수 필드 누락 (question 없음) → 400 INVALID_INPUT_VALUE")
        void missingRequiredField() throws Exception {
            String body = objectMapper.createObjectNode()
                    .put("answer", "답변")
                    .put("category", "RECRUITMENT")
                    .put("order_num", 1)
                    .toString();

            mockMvc.perform(post("/api/v1/admin/faqs")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/admin/faqs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("질문", "답변", "RECRUITMENT", 1)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────
    // FAQ-003: PATCH /api/v1/admin/faqs/{faqId}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("FAQ-003 PATCH /api/v1/admin/faqs/{faqId}")
    class UpdateFaq {

        @Test
        @DisplayName("TC-005 부분 수정 성공 → 200 OK")
        void success() throws Exception {
            when(faqService.updateFaq(eq(1L), any())).thenReturn(makeFaqResponse(1L, Faq.Category.RECRUITMENT, 1));

            String body = objectMapper.createObjectNode()
                    .put("question", "변경된 질문")
                    .toString();

            mockMvc.perform(patch("/api/v1/admin/faqs/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("TC-007 존재하지 않는 faqId → 404 FAQ_NOT_FOUND")
        void notFound() throws Exception {
            when(faqService.updateFaq(eq(999L), any()))
                    .thenThrow(new CustomException(ErrorCode.FAQ_NOT_FOUND));

            mockMvc.perform(patch("/api/v1/admin/faqs/999")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("FAQ_NOT_FOUND"));
        }

        @Test
        @DisplayName("TC-008 수정 후 orderNum 충돌 → 409 DUPLICATE_ORDER_NUM")
        void duplicateOnUpdate() throws Exception {
            when(faqService.updateFaq(eq(2L), any()))
                    .thenThrow(new CustomException(ErrorCode.DUPLICATE_ORDER_NUM));

            String body = objectMapper.createObjectNode()
                    .put("order_num", 1)
                    .toString();

            mockMvc.perform(patch("/api/v1/admin/faqs/2")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DUPLICATE_ORDER_NUM"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/faqs/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────
    // FAQ-004: DELETE /api/v1/admin/faqs/{faqId}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("FAQ-004 DELETE /api/v1/admin/faqs/{faqId}")
    class DeleteFaq {

        @Test
        @DisplayName("TC-009 FAQ 삭제 성공 → 200 OK")
        void success() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/faqs/1")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("TC-010 존재하지 않는 faqId → 404 FAQ_NOT_FOUND")
        void notFound() throws Exception {
            org.mockito.Mockito.doThrow(new CustomException(ErrorCode.FAQ_NOT_FOUND))
                    .when(faqService).deleteFaq(999L);

            mockMvc.perform(delete("/api/v1/admin/faqs/999")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("FAQ_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/faqs/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
