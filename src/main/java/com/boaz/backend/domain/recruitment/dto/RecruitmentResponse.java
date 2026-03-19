package com.boaz.backend.domain.recruitment.dto;

import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecruitmentResponse {

    private final Integer term;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final JsonNode schedule;
    private final String brochureUrl;
    private final Boolean isActive;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private RecruitmentResponse(Recruitment recruitment, boolean isActive) {
        this.term = recruitment.getTerm();
        this.startDate = recruitment.getStartDate();
        this.endDate = recruitment.getEndDate();
        try {
                this.schedule = objectMapper.readTree(recruitment.getSchedule());
            } catch (Exception e) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        this.brochureUrl = recruitment.getBrochureUrl();
        this.isActive = isActive;
    }

    private RecruitmentResponse(boolean isActive) {
        this.term = null;
        this.startDate = null;
        this.endDate = null;
        this.schedule = null;
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