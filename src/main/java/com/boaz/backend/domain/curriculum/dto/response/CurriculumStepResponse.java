package com.boaz.backend.domain.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class CurriculumStepResponse {

    @Schema(description = "단계 번호", example = "1")
    private final int step;

    @Schema(description = "단계 제목", example = "데이터 분석 기초")
    private final String title;

    @Schema(description = "단계 설명", example = "통계 기초, 파이썬 기초")
    private final String desc;

    public CurriculumStepResponse(int step, String title, String desc) {
        this.step = step;
        this.title = title;
        this.desc = desc;
    }
}
