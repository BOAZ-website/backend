package com.boaz.backend.domain.recruitment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ApplicationResponse {

    @Schema(description = "지원자 ID", example = "1")
    @JsonProperty("applicant_id")
    private final Long applicantId;

    @Schema(description = "제출 일시", example = "2024-03-01T12:00:00")
    @JsonProperty("submitted_at")
    private final LocalDateTime submittedAt;

    private ApplicationResponse(Long applicantId, LocalDateTime submittedAt) {
        this.applicantId = applicantId;
        this.submittedAt = submittedAt;
    }

    public static ApplicationResponse of(Long applicantId, LocalDateTime submittedAt) {
        return new ApplicationResponse(applicantId, submittedAt);
    }
}