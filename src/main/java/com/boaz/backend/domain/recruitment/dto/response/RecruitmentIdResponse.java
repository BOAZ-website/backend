package com.boaz.backend.domain.recruitment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class RecruitmentIdResponse {

    @Schema(description = "모집 공고 ID", example = "12")
    private final Long recruitmentId;

    private RecruitmentIdResponse(Long recruitmentId) {
        this.recruitmentId = recruitmentId;
    }

    public static RecruitmentIdResponse of(Long recruitmentId) {
        return new RecruitmentIdResponse(recruitmentId);
    }
}
