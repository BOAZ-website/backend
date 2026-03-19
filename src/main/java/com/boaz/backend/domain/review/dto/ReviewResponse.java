package com.boaz.backend.domain.review.dto;

import com.boaz.backend.domain.review.entity.Review;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class ReviewResponse {

    private final Long id;
    private final String name;
    private final String track;
    private final Integer term;
    private final String content;

    @JsonProperty("image_url")
    private final String imageUrl;

    private ReviewResponse(Long id, String name, String track, Integer term, String content, String imageUrl) {
        this.id = id;
        this.name = name;
        this.track = track;
        this.term = term;
        this.content = content;
        this.imageUrl = imageUrl;
    }

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getName(),
                review.getTrack().name(),
                review.getTerm(),
                review.getContent(),
                review.getImageUrl()
        );
    }
}
