package com.boaz.backend.domain.recruitment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ApplicationResponse {

    @JsonProperty("applicant_id")
    private final Long applicantId;

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