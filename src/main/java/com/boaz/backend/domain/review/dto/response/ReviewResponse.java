package com.boaz.backend.domain.review.dto.response;

import com.boaz.backend.domain.review.entity.Review;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class ReviewResponse {

    @Schema(description = "후기 ID", example = "1")
    private final Long id;

    @Schema(description = "작성자 이름", example = "김분석")
    private final String name;

    @Schema(description = "부문", example = "ANALYSIS", allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private final String track;

    @Schema(description = "기수", example = "15")
    private final Integer term;

    @Schema(description = "후기 내용", example = "BOAZ에서 데이터 분석을 공부하며 정말 많이 성장했습니다. 실전 프로젝트를 통해 이론으로만 알던 머신러닝을 직접 적용해볼 수 있어서 좋았어요.")
    private final String content;

    @JsonProperty("image_url")
    @Schema(description = "프로필 이미지 URL", example = "https://example.com/images/review1.jpg", nullable = true)
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
