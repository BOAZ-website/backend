package com.boaz.backend.domain.recruitment.dto;

import lombok.Getter;

@Getter
public class RecruitmentStatusResponse {

    private final Boolean isActive;

    private RecruitmentStatusResponse(boolean isActive) {
        this.isActive = isActive;
    }

    public static RecruitmentStatusResponse of(boolean isActive) {
        return new RecruitmentStatusResponse(isActive);
    }
}