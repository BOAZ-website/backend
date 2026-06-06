package com.boaz.backend.domain.review.controller;

import com.boaz.backend.domain.review.dto.response.ReviewResponse;
import com.boaz.backend.domain.review.entity.Review;
import com.boaz.backend.domain.review.service.ReviewService;
import com.boaz.backend.global.common.enums.Track;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(
        value = ReviewAdminController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class ReviewAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ReviewService reviewService;

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    private ReviewResponse makeReviewResponse(Long id, Track track, int term) {
        Review r = Review.create("홍길동", track, term, "후기 내용", null);
        ReflectionTestUtils.setField(r, "id", id);
        return ReviewResponse.from(r);
    }

    private String createBody(String track) {
        return objectMapper.createObjectNode()
                .put("name", "홍길동")
                .put("track", track)
                .put("term", 26)
                .put("content", "좋은 경험이었습니다.")
                .toString();
    }

    // ──────────────────────────────────────────────
    // REV-002: POST /api/v1/admin/reviews
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REV-002 POST /api/v1/admin/reviews")
    class CreateReview {

        @Test
        @DisplayName("TC-007 후기 등록 성공 → 201 Created")
        void success() throws Exception {
            when(reviewService.createReview(any()))
                    .thenReturn(makeReviewResponse(1L, Track.ANALYSIS, 26));

            mockMvc.perform(post("/api/v1/admin/reviews")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("ANALYSIS")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.track").value("ANALYSIS"))
                    .andExpect(jsonPath("$.data.term").value(26));
        }

        @Test
        @DisplayName("TC-008 track=ALL → 400 INVALID_TRACK_SELECTION")
        void trackAll() throws Exception {
            when(reviewService.createReview(any()))
                    .thenThrow(new CustomException(ErrorCode.INVALID_TRACK_SELECTION));

            mockMvc.perform(post("/api/v1/admin/reviews")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("ALL")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_TRACK_SELECTION"));
        }

        @Test
        @DisplayName("필수 필드 누락 (name 없음) → 400")
        void missingRequiredField() throws Exception {
            String body = objectMapper.createObjectNode()
                    .put("track", "ANALYSIS")
                    .put("term", 26)
                    .put("content", "내용")
                    .toString();

            mockMvc.perform(post("/api/v1/admin/reviews")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/admin/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("ANALYSIS")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────
    // REV-003: PATCH /api/v1/admin/reviews/{reviewId}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REV-003 PATCH /api/v1/admin/reviews/{reviewId}")
    class UpdateReview {

        @Test
        @DisplayName("TC-009 부분 수정 성공 → 200 OK")
        void success() throws Exception {
            when(reviewService.updateReview(eq(1L), any()))
                    .thenReturn(makeReviewResponse(1L, Track.ANALYSIS, 26));

            String body = objectMapper.createObjectNode()
                    .put("content", "수정된 후기")
                    .toString();

            mockMvc.perform(patch("/api/v1/admin/reviews/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("TC-010 존재하지 않는 reviewId → 404 REVIEW_NOT_FOUND")
        void notFound() throws Exception {
            when(reviewService.updateReview(eq(999L), any()))
                    .thenThrow(new CustomException(ErrorCode.REVIEW_NOT_FOUND));

            mockMvc.perform(patch("/api/v1/admin/reviews/999")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("REVIEW_NOT_FOUND"));
        }

        @Test
        @DisplayName("TC-011 track=ALL로 수정 시도 → 400 INVALID_TRACK_SELECTION")
        void trackAll() throws Exception {
            when(reviewService.updateReview(eq(1L), any()))
                    .thenThrow(new CustomException(ErrorCode.INVALID_TRACK_SELECTION));

            String body = objectMapper.createObjectNode().put("track", "ALL").toString();

            mockMvc.perform(patch("/api/v1/admin/reviews/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_TRACK_SELECTION"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/reviews/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────
    // REV-004: DELETE /api/v1/admin/reviews/{reviewId}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REV-004 DELETE /api/v1/admin/reviews/{reviewId}")
    class DeleteReview {

        @Test
        @DisplayName("TC-012 삭제 성공 → 200 OK")
        void success() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/reviews/1")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("TC-013 존재하지 않는 reviewId → 404 REVIEW_NOT_FOUND")
        void notFound() throws Exception {
            doThrow(new CustomException(ErrorCode.REVIEW_NOT_FOUND))
                    .when(reviewService).deleteReview(999L);

            mockMvc.perform(delete("/api/v1/admin/reviews/999")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("REVIEW_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/reviews/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
