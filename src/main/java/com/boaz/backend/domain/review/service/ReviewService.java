package com.boaz.backend.domain.review.service;

import com.boaz.backend.domain.review.dto.request.ReviewCreateRequest;
import com.boaz.backend.domain.review.dto.request.ReviewUpdateRequest;
import com.boaz.backend.domain.review.dto.response.ReviewResponse;
import com.boaz.backend.domain.review.entity.Review;
import com.boaz.backend.domain.review.repository.ReviewRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;

    @Transactional
    public ReviewResponse createReview(ReviewCreateRequest request) {
        request.getTrack().validateNotAll();
        Review review = Review.create(
                request.getName(),
                request.getTrack(),
                request.getTerm(),
                request.getContent(),
                request.getImageUrl()
        );
        reviewRepository.save(review);
        return ReviewResponse.from(review);
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));
        if (request.getTrack() != null) {
            request.getTrack().validateNotAll();
        }
        review.update(
                request.getName(),
                request.getTrack(),
                request.getTerm(),
                request.getContent(),
                request.getImageUrl()
        );
        return ReviewResponse.from(review);
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));
        reviewRepository.delete(review);
    }

    public List<ReviewResponse> getReviews(Track track, Integer term) {
        List<Review> reviews;

        if (track != null && term != null) {
            reviews = reviewRepository.findAllByTrackAndTermOrderByTermDesc(
                    track, term);
        } else if (track != null) {
            reviews = reviewRepository.findAllByTrackOrderByTermDesc(
                    track);
        } else if (term != null) {
            reviews = reviewRepository.findAllByTermOrderByTermDesc(term);
        } else {
            reviews = reviewRepository.findAllByOrderByTermDesc();
        }

        return reviews.stream()
                .map(ReviewResponse::from)
                .toList();
    }
}
