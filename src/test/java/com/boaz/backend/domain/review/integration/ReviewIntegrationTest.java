package com.boaz.backend.domain.review.integration;

import com.boaz.backend.domain.review.dto.request.ReviewCreateRequest;
import com.boaz.backend.domain.review.dto.request.ReviewUpdateRequest;
import com.boaz.backend.domain.review.dto.response.ReviewResponse;
import com.boaz.backend.domain.review.entity.Review;
import com.boaz.backend.domain.review.repository.ReviewRepository;
import com.boaz.backend.domain.review.service.ReviewService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class ReviewIntegrationTest extends TestcontainersBase {

    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private Review saveReview(Track track, int term) {
        return reviewRepository.save(Review.create("홍길동", track, term, "후기 내용", null));
    }

    private ReviewCreateRequest makeCreateRequest(Track track, int term) {
        ReviewCreateRequest req = new ReviewCreateRequest();
        ReflectionTestUtils.setField(req, "name", "홍길동");
        ReflectionTestUtils.setField(req, "track", track);
        ReflectionTestUtils.setField(req, "term", term);
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
    @DisplayName("후기 조회 end-to-end (REV-001)")
    class GetReviews {

        @Test
        @DisplayName("TC-001 파라미터 없음 → term 내림차순 전체 반환")
        void allReviews() {
            saveReview(Track.ANALYSIS, 24);
            saveReview(Track.ENGINEERING, 26);
            saveReview(Track.VISUALIZATION, 25);

            List<ReviewResponse> result = reviewService.getReviews(null, null);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getTerm()).isEqualTo(26);
            assertThat(result.get(1).getTerm()).isEqualTo(25);
            assertThat(result.get(2).getTerm()).isEqualTo(24);
        }

        @Test
        @DisplayName("TC-002 track=ANALYSIS 필터 → 해당 트랙만 반환")
        void byTrack() {
            saveReview(Track.ANALYSIS, 26);
            saveReview(Track.ENGINEERING, 26);

            List<ReviewResponse> result = reviewService.getReviews(Track.ANALYSIS, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTrack()).isEqualTo("ANALYSIS");
        }

        @Test
        @DisplayName("TC-003 term=26 필터 → 26기만 반환")
        void byTerm() {
            saveReview(Track.ANALYSIS, 26);
            saveReview(Track.ENGINEERING, 26);
            saveReview(Track.ANALYSIS, 25);

            List<ReviewResponse> result = reviewService.getReviews(null, 26);

            assertThat(result).hasSize(2);
            assertThat(result).noneMatch(r -> r.getTerm() != 26);
        }

        @Test
        @DisplayName("TC-004 track+term 동시 필터 → 교집합 반환")
        void byTrackAndTerm() {
            saveReview(Track.ANALYSIS, 26);
            saveReview(Track.ENGINEERING, 26);
            saveReview(Track.ANALYSIS, 25);

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
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
        }

        @Test
        @DisplayName("TC-006 결과 없을 때 → 빈 배열")
        void emptyResult() {
            List<ReviewResponse> result = reviewService.getReviews(null, 99);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════
    // REV-002: 후기 등록
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("후기 등록 end-to-end (REV-002)")
    class CreateReview {

        @Test
        @DisplayName("TC-007 후기 등록 성공 → DB에 저장됨")
        void success() {
            ReviewResponse res = reviewService.createReview(makeCreateRequest(Track.ANALYSIS, 26));

            assertThat(res.getTrack()).isEqualTo("ANALYSIS");
            assertThat(reviewRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-008 track=ALL → INVALID_TRACK_SELECTION, DB 미저장")
        void trackAll() {
            assertThatThrownBy(() -> reviewService.createReview(makeCreateRequest(Track.ALL, 26)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);

            assertThat(reviewRepository.count()).isZero();
        }
    }

    // ══════════════════════════════════════════════
    // REV-003: 후기 수정
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("후기 수정 end-to-end (REV-003)")
    class UpdateReview {

        @Test
        @DisplayName("TC-009 content만 부분 수정 → 나머지 필드 기존 값 유지")
        void partialUpdate() {
            Review saved = saveReview(Track.ANALYSIS, 26);

            ReviewResponse res = reviewService.updateReview(saved.getId(),
                    makeUpdateRequest(null, null, null, "수정된 후기", null));

            assertThat(res.getContent()).isEqualTo("수정된 후기");
            assertThat(res.getName()).isEqualTo("홍길동");
            assertThat(res.getTrack()).isEqualTo("ANALYSIS");
            assertThat(res.getTerm()).isEqualTo(26);
        }

        @Test
        @DisplayName("TC-010 존재하지 않는 reviewId → REVIEW_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> reviewService.updateReview(999L,
                    makeUpdateRequest(null, null, null, "수정", null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-011 track=ALL로 수정 시도 → INVALID_TRACK_SELECTION")
        void trackAll() {
            Review saved = saveReview(Track.ANALYSIS, 26);

            assertThatThrownBy(() -> reviewService.updateReview(saved.getId(),
                    makeUpdateRequest(null, Track.ALL, null, null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
        }
    }

    // ══════════════════════════════════════════════
    // REV-004: 후기 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("후기 삭제 end-to-end (REV-004)")
    class DeleteReview {

        @Test
        @DisplayName("TC-012 삭제 성공 → DB에서 제거됨")
        void success() {
            Review saved = saveReview(Track.ANALYSIS, 26);

            reviewService.deleteReview(saved.getId());

            assertThat(reviewRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("TC-013 존재하지 않는 reviewId → REVIEW_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> reviewService.deleteReview(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // 생명주기 통합 시나리오
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("후기 전체 생명주기 시나리오")
    class Lifecycle {

        @Test
        @DisplayName("등록 → 필터 조회 → 수정 → 삭제 흐름이 DB와 일치")
        void fullLifecycle() {
            ReviewResponse created = reviewService.createReview(
                    makeCreateRequest(Track.ENGINEERING, 26));
            assertThat(reviewRepository.count()).isEqualTo(1);

            List<ReviewResponse> list = reviewService.getReviews(Track.ENGINEERING, 26);
            assertThat(list).hasSize(1);
            assertThat(list.get(0).getContent()).isEqualTo("좋은 경험이었습니다.");

            ReviewResponse updated = reviewService.updateReview(created.getId(),
                    makeUpdateRequest(null, null, null, "수정된 후기", null));
            assertThat(updated.getContent()).isEqualTo("수정된 후기");
            assertThat(updated.getTrack()).isEqualTo("ENGINEERING");

            reviewService.deleteReview(created.getId());
            assertThat(reviewRepository.findById(created.getId())).isEmpty();
            assertThat(reviewService.getReviews(Track.ENGINEERING, 26)).isEmpty();
        }
    }
}
