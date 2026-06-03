package com.boaz.backend.domain.review.controller;

import com.boaz.backend.domain.review.dto.response.ReviewResponse;
import com.boaz.backend.domain.review.entity.Review;
import com.boaz.backend.domain.review.service.ReviewService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(
        value = ReviewController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class ReviewControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ReviewService reviewService;

    private ReviewResponse makeReviewResponse(Long id, Track track, int term) {
        Review r = Review.create("홍길동", track, term, "후기 내용", null);
        ReflectionTestUtils.setField(r, "id", id);
        return ReviewResponse.from(r);
    }

    // ──────────────────────────────────────────────
    // REV-001: GET /api/v1/reviews
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("REV-001 GET /api/v1/reviews")
    class GetReviews {

        @Test
        @DisplayName("TC-001 파라미터 없음 → 200 + term 내림차순 전체 목록")
        void allReviews() throws Exception {
            when(reviewService.getReviews(isNull(), isNull())).thenReturn(List.of(
                    makeReviewResponse(1L, Track.ENGINEERING, 26),
                    makeReviewResponse(2L, Track.ANALYSIS, 25)
            ));

            mockMvc.perform(get("/api/v1/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].term").value(26))
                    .andExpect(jsonPath("$.data[1].term").value(25));
        }

        @Test
        @DisplayName("TC-002 track=ANALYSIS 필터 → 200 + 해당 트랙만")
        void byTrack() throws Exception {
            when(reviewService.getReviews(Track.ANALYSIS, null))
                    .thenReturn(List.of(makeReviewResponse(1L, Track.ANALYSIS, 26)));

            mockMvc.perform(get("/api/v1/reviews").param("track", "ANALYSIS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].track").value("ANALYSIS"));
        }

        @Test
        @DisplayName("TC-003 term=26 필터 → 200 + 26기만")
        void byTerm() throws Exception {
            when(reviewService.getReviews(null, 26)).thenReturn(List.of(
                    makeReviewResponse(1L, Track.ANALYSIS, 26),
                    makeReviewResponse(2L, Track.ENGINEERING, 26)
            ));

            mockMvc.perform(get("/api/v1/reviews").param("term", "26"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("TC-004 track+term 동시 필터 → 200 + 교집합")
        void byTrackAndTerm() throws Exception {
            when(reviewService.getReviews(Track.ANALYSIS, 26))
                    .thenReturn(List.of(makeReviewResponse(1L, Track.ANALYSIS, 26)));

            mockMvc.perform(get("/api/v1/reviews")
                            .param("track", "ANALYSIS")
                            .param("term", "26"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("TC-005 track=ALL → 400 INVALID_TRACK_SELECTION")
        void trackAll() throws Exception {
            when(reviewService.getReviews(Track.ALL, null))
                    .thenThrow(new CustomException(ErrorCode.INVALID_TRACK_SELECTION));

            mockMvc.perform(get("/api/v1/reviews").param("track", "ALL"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_TRACK_SELECTION"));
        }

        @Test
        @DisplayName("TC-006 결과 없을 때 → 200 + 빈 배열")
        void emptyResult() throws Exception {
            when(reviewService.getReviews(any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/reviews").param("term", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("[Security] 인증 없이 접근 가능 (공개 API)")
        void publicApi() throws Exception {
            when(reviewService.getReviews(any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/reviews"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("image_url 필드명 확인 → snake_case")
        void imageUrlFieldName() throws Exception {
            Review r = Review.create("홍길동", Track.ANALYSIS, 26, "내용", "https://img.url");
            ReflectionTestUtils.setField(r, "id", 1L);
            when(reviewService.getReviews(isNull(), isNull()))
                    .thenReturn(List.of(ReviewResponse.from(r)));

            mockMvc.perform(get("/api/v1/reviews"))
                    .andExpect(jsonPath("$.data[0].image_url").value("https://img.url"));
        }
    }
}
