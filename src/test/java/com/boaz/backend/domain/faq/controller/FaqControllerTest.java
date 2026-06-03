package com.boaz.backend.domain.faq.controller;

import com.boaz.backend.domain.faq.dto.response.FaqResponse;
import com.boaz.backend.domain.faq.entity.Faq;
import com.boaz.backend.domain.faq.service.FaqService;
import com.boaz.backend.support.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(
        value = FaqController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class FaqControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean FaqService faqService;

    private FaqResponse makeFaqResponse(Long id, Faq.Category category, int orderNum) {
        Faq faq = Faq.create("질문 " + id, "답변 " + id, category, orderNum);
        ReflectionTestUtils.setField(faq, "id", id);
        return FaqResponse.from(faq);
    }

    // ──────────────────────────────────────────────
    // FAQ-001: GET /api/v1/archiving/faqs
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("FAQ-001 GET /api/v1/archiving/faqs")
    class GetFaqs {

        @Test
        @DisplayName("TC-001 정상 조회 → 200 + orderNum 오름차순 목록")
        void success() throws Exception {
            when(faqService.getFaqs(Faq.Category.RECRUITMENT))
                    .thenReturn(List.of(
                            makeFaqResponse(1L, Faq.Category.RECRUITMENT, 1),
                            makeFaqResponse(2L, Faq.Category.RECRUITMENT, 2)
                    ));

            mockMvc.perform(get("/api/v1/archiving/faqs")
                            .param("category", "RECRUITMENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].order_num").value(1))
                    .andExpect(jsonPath("$.data[1].order_num").value(2))
                    .andExpect(jsonPath("$.data[0].category").value("RECRUITMENT"));
        }

        @Test
        @DisplayName("TC-002 해당 카테고리 데이터 없을 때 → 200 + 빈 배열")
        void emptyResult() throws Exception {
            when(faqService.getFaqs(Faq.Category.ETC)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/archiving/faqs")
                            .param("category", "ETC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("[Security] 인증 없이 접근 가능 (공개 API)")
        void publicApi() throws Exception {
            when(faqService.getFaqs(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/archiving/faqs")
                            .param("category", "ACTIVITY"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("category 파라미터 누락 → 400")
        void missingCategory() throws Exception {
            mockMvc.perform(get("/api/v1/archiving/faqs"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("존재하지 않는 category 값 입력 → 400")
        void invalidCategory() throws Exception {
            mockMvc.perform(get("/api/v1/archiving/faqs")
                            .param("category", "INVALID"))
                    .andExpect(status().isBadRequest());
        }
    }
}
