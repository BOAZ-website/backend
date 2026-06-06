package com.boaz.backend.domain.recruitment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecruitmentStatusResponse {

    @Schema(description = "모집 중 여부", example = "true")
    private final Boolean isActive;

    @Schema(description = "현재 모집 기수", example = "25", nullable = true)
    private final Integer term;

    private RecruitmentStatusResponse(boolean isActive, Integer term) {
        this.isActive = isActive;
        this.term = term;
    }

    public static RecruitmentStatusResponse of(boolean isActive, Integer term) {
        return new RecruitmentStatusResponse(isActive, term);
    }
}