package com.boaz.backend.domain.review.repository;

import com.boaz.backend.domain.review.entity.Review;
import com.boaz.backend.global.common.enums.Track;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findAllByOrderByTermDesc();

    List<Review> findAllByTrackOrderByTermDesc(Track track);

    List<Review> findAllByTermOrderByTermDesc(Integer term);

    List<Review> findAllByTrackAndTermOrderByTermDesc(Track track, Integer term);
}
