package com.boaz.backend.domain.review.dto.request;

import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class ReviewUpdateRequest {

    @Schema(description = "작성자 이름", example = "김분석", nullable = true)
    private String name;

    @Schema(description = "부문", example = "ANALYSIS", allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"}, nullable = true)
    private Track track;

    @Positive(message = "기수는 양의 정수여야 합니다.")
    @Schema(description = "기수", example = "15", nullable = true)
    private Integer term;

    @Schema(description = "후기 내용", example = "BOAZ에서 많이 성장했습니다.", nullable = true)
    private String content;

    @Schema(description = "프로필 이미지 URL", example = "https://example.com/image.jpg", nullable = true)
    private String imageUrl;
}
