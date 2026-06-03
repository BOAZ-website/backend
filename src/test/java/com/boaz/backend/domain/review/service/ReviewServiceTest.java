package com.boaz.backend.domain.review.service;

import com.boaz.backend.domain.review.dto.request.ReviewCreateRequest;
import com.boaz.backend.domain.review.dto.request.ReviewUpdateRequest;
import com.boaz.backend.domain.review.dto.response.ReviewResponse;
import com.boaz.backend.domain.review.entity.Review;
import com.boaz.backend.domain.review.repository.ReviewRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @InjectMocks ReviewService reviewService;
    @Mock ReviewRepository reviewRepository;

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private Review makeReview(Long id, Track track, int term) {
        Review r = Review.create("홍길동", track, term, "후기 내용", null);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private ReviewCreateRequest makeCreateRequest(Track track) {
        ReviewCreateRequest req = new ReviewCreateRequest();
        ReflectionTestUtils.setField(req, "name", "홍길동");
        ReflectionTestUtils.setField(req, "track", track);
        ReflectionTestUtils.setField(req, "term", 26);
        ReflectionTestUtils.setField(req, "content", "좋은 경험이었습니다.");
        ReflectionTestUtils.setField(req, "imageUrl", null);
        return req;
    }

    private ReviewUpdateRequest makeUpdateRequest(String name, Track track, Integer term,
                                                  String content, String imageUrl) {
        ReviewUpdateRequest req = new ReviewUpdateRequest();
        ReflectionTestUtils.setField(req, "name", name);
        ReflectionTestUtils.setField(req, "track", track);
        ReflectionTestUtils.setField(req, "term", term);
        ReflectionTestUtils.setField(req, "content", content);
        ReflectionTestUtils.setField(req, "imageUrl", imageUrl);
        return req;
    }

    // ══════════════════════════════════════════════
    // REV-001: 후기 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REV-001 후기 조회")
    class GetReviews {

        @Test
        @DisplayName("TC-001 파라미터 없음 → 전체 term 내림차순 반환")
        void allReviews() {
            Review r1 = makeReview(1L, Track.ENGINEERING, 26);
            Review r2 = makeReview(2L, Track.ANALYSIS, 25);
            when(reviewRepository.findAllByOrderByTermDesc()).thenReturn(List.of(r1, r2));

            List<ReviewResponse> result = reviewService.getReviews(null, null);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTerm()).isEqualTo(26);
            assertThat(result.get(1).getTerm()).isEqualTo(25);
        }

        @Test
        @DisplayName("TC-002 track=ANALYSIS 필터 → 해당 트랙만 반환")
        void byTrack() {
            when(reviewRepository.findAllByTrackOrderByTermDesc(Track.ANALYSIS))
                    .thenReturn(List.of(makeReview(1L, Track.ANALYSIS, 26)));

            List<ReviewResponse> result = reviewService.getReviews(Track.ANALYSIS, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTrack()).isEqualTo("ANALYSIS");
        }

        @Test
        @DisplayName("TC-003 term=26 필터 → 26기만 반환")
        void byTerm() {
            when(reviewRepository.findAllByTermOrderByTermDesc(26))
                    .thenReturn(List.of(
                            makeReview(1L, Track.ANALYSIS, 26),
                            makeReview(2L, Track.ENGINEERING, 26)
                    ));

            List<ReviewResponse> result = reviewService.getReviews(null, 26);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(r -> r.getTerm()).containsOnly(26);
        }

        @Test
        @DisplayName("TC-004 track+term 동시 필터 → 교집합 반환")
        void byTrackAndTerm() {
            when(reviewRepository.findAllByTrackAndTermOrderByTermDesc(Track.ANALYSIS, 26))
                    .thenReturn(List.of(makeReview(1L, Track.ANALYSIS, 26)));

            List<ReviewResponse> result = reviewService.getReviews(Track.ANALYSIS, 26);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTrack()).isEqualTo("ANALYSIS");
            assertThat(result.get(0).getTerm()).isEqualTo(26);
        }

        @Test
        @DisplayName("TC-005 track=ALL → INVALID_TRACK_SELECTION")
        void trackAll() {
            assertThatThrownBy(() -> reviewService.getReviews(Track.ALL, null))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
        }

        @Test
        @DisplayName("TC-006 조회 결과 없을 때 → 빈 배열 반환")
        void emptyResult() {
            when(reviewRepository.findAllByTermOrderByTermDesc(99)).thenReturn(List.of());

            List<ReviewResponse> result = reviewService.getReviews(null, 99);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════
    // REV-002: 후기 등록
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REV-002 후기 등록")
    class CreateReview {

        @Test
        @DisplayName("TC-007 후기 등록 성공 → 저장 후 응답 반환")
        void success() {
            when(reviewRepository.save(any())).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                ReflectionTestUtils.setField(r, "id", 1L);
                return r;
            });

            ReviewResponse res = reviewService.createReview(makeCreateRequest(Track.ANALYSIS));

            assertThat(res.getTrack()).isEqualTo("ANALYSIS");
            assertThat(res.getTerm()).isEqualTo(26);
            verify(reviewRepository).save(any(Review.class));
        }

        @Test
        @DisplayName("TC-008 track=ALL → INVALID_TRACK_SELECTION, 저장 안 함")
        void trackAll() {
            assertThatThrownBy(() -> reviewService.createReview(makeCreateRequest(Track.ALL)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);

            verify(reviewRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════
    // REV-003: 후기 수정
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REV-003 후기 수정")
    class UpdateReview {

        @Test
        @DisplayName("TC-009 content만 부분 수정 → name·track·term·imageUrl 기존 값 유지")
        void partialUpdate() {
            Review review = makeReview(1L, Track.ANALYSIS, 26);
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

            ReviewResponse res = reviewService.updateReview(1L,
                    makeUpdateRequest(null, null, null, "수정된 후기", null));

            assertThat(res.getContent()).isEqualTo("수정된 후기");
            assertThat(res.getName()).isEqualTo("홍길동");
            assertThat(res.getTrack()).isEqualTo("ANALYSIS");
            assertThat(res.getTerm()).isEqualTo(26);
        }

        @Test
        @DisplayName("TC-010 존재하지 않는 reviewId → REVIEW_NOT_FOUND")
        void notFound() {
            when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.updateReview(999L,
                    makeUpdateRequest(null, null, null, "수정", null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-011 track=ALL로 수정 시도 → INVALID_TRACK_SELECTION")
        void trackAll() {
            Review review = makeReview(1L, Track.ANALYSIS, 26);
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

            assertThatThrownBy(() -> reviewService.updateReview(1L,
                    makeUpdateRequest(null, Track.ALL, null, null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
        }

        @Test
        @DisplayName("track=null로 수정 시 validateNotAll 미호출 → 기존 track 유지")
        void trackNullNoValidation() {
            Review review = makeReview(1L, Track.ANALYSIS, 26);
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

            ReviewResponse res = reviewService.updateReview(1L,
                    makeUpdateRequest(null, null, null, null, null));

            assertThat(res.getTrack()).isEqualTo("ANALYSIS");
        }
    }

    // ══════════════════════════════════════════════
    // REV-004: 후기 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("REV-004 후기 삭제")
    class DeleteReview {

        @Test
        @DisplayName("TC-012 삭제 성공 → repository.delete 호출")
        void success() {
            Review review = makeReview(1L, Track.ANALYSIS, 26);
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

            reviewService.deleteReview(1L);

            verify(reviewRepository).delete(review);
        }

        @Test
        @DisplayName("TC-013 존재하지 않는 reviewId → REVIEW_NOT_FOUND, 삭제 안 함")
        void notFound() {
            when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.deleteReview(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_NOT_FOUND);

            verify(reviewRepository, never()).delete(any());
        }
    }
}
