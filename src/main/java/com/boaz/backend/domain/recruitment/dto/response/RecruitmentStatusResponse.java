package com.boaz.backend.domain.recruitment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecruitmentStatusResponse {

    private final Boolean isActive;
    private final Integer term;

    private RecruitmentStatusResponse(boolean isActive, Integer term) {
        this.isActive = isActive;
        this.term = term;
    }

    public static RecruitmentStatusResponse of(boolean isActive, Integer term) {
        return new RecruitmentStatusResponse(isActive, term);
    }
}