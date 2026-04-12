package com.boaz.backend.domain.review.service;

import com.boaz.backend.domain.review.dto.response.ReviewResponse;
import com.boaz.backend.domain.review.entity.Review;
import com.boaz.backend.domain.review.repository.ReviewRepository;
import com.boaz.backend.global.common.enums.Track;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;

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
