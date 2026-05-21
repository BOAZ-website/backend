package com.boaz.backend.domain.recruitment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class DraftApplicationResponse {

    @Schema(description = "지원자 ID", example = "42")
    @JsonProperty("applicant_id")
    private final Long applicantId;

    private DraftApplicationResponse(Long applicantId) {
        this.applicantId = applicantId;
    }

    public static DraftApplicationResponse of(Long applicantId) {
        return new DraftApplicationResponse(applicantId);
    }
}
