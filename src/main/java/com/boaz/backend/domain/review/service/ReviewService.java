package com.boaz.backend.domain.review.service;

import com.boaz.backend.domain.review.dto.ReviewResponse;
import com.boaz.backend.domain.review.entity.Review;
import com.boaz.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public List<ReviewResponse> getReviews(String track, Integer term) {
        List<Review> reviews;

        if (track != null && term != null) {
            reviews = reviewRepository.findAllByTrackAndTermOrderByTermDesc(
                    Review.Track.valueOf(track), term);
        } else if (track != null) {
            reviews = reviewRepository.findAllByTrackOrderByTermDesc(
                    Review.Track.valueOf(track));
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
