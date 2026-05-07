package com.boaz.backend.domain.review.dto.request;

import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class ReviewCreateRequest {

    @NotBlank(message = "이름은 필수입니다.")
    @Schema(description = "작성자 이름", example = "김분석")
    private String name;

    @NotNull(message = "부문은 필수입니다.")
    @Schema(description = "부문", example = "ANALYSIS", allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private Track track;

    @NotNull(message = "기수는 필수입니다.")
    @Positive(message = "기수는 양의 정수여야 합니다.")
    @Schema(description = "기수", example = "15")
    private Integer term;

    @NotBlank(message = "내용은 필수입니다.")
    @Schema(description = "후기 내용", example = "BOAZ에서 많이 성장했습니다.")
    private String content;

    @Schema(description = "프로필 이미지 URL (선택)", example = "https://example.com/image.jpg", nullable = true)
    private String imageUrl;
}
