package com.boaz.backend.domain.curriculum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class CurriculumStepRequest {

    @NotNull(message = "단계 번호는 필수입니다.")
    @Positive(message = "단계 번호는 양의 정수여야 합니다.")
    @Schema(description = "단계 번호", example = "1")
    private Integer step;

    @NotBlank(message = "단계 제목은 필수입니다.")
    @Schema(description = "단계 제목", example = "데이터 분석 기초")
    private String title;

    @NotBlank(message = "단계 설명은 필수입니다.")
    @Schema(description = "단계 설명", example = "통계 기초, 파이썬 기초")
    private String desc;
}
