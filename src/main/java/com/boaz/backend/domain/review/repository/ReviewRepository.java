package com.boaz.backend.domain.review.repository;

import com.boaz.backend.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findAllByOrderByTermDesc();

    List<Review> findAllByTrackOrderByTermDesc(Review.Track track);

    List<Review> findAllByTermOrderByTermDesc(Integer term);

    List<Review> findAllByTrackAndTermOrderByTermDesc(Review.Track track, Integer term);
}
