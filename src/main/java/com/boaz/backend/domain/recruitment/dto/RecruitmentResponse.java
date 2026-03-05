package com.boaz.backend.domain.recruitment.dto;

import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecruitmentResponse {

    private final Integer term;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final String brochureUrl;
    private final Boolean isActive;

    private RecruitmentResponse(Recruitment recruitment, boolean isActive) {
        this.term = recruitment.getTerm();
        this.startDate = recruitment.getStartDate();
        this.endDate = recruitment.getEndDate();
        this.brochureUrl = recruitment.getBrochureUrl();
        this.isActive = isActive;
    }

    private RecruitmentResponse(boolean isActive) {
        this.term = null;
        this.startDate = null;
        this.endDate = null;
        this.brochureUrl = null;
        this.isActive = isActive;
    }

    public static RecruitmentResponse from(Recruitment recruitment) {
        return new RecruitmentResponse(recruitment, true);
    }

    public static RecruitmentResponse inactive() {
        return new RecruitmentResponse(false);
    }
}