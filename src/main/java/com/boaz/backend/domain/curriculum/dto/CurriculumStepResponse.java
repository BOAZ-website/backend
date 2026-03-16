package com.boaz.backend.domain.curriculum.dto;

import lombok.Getter;

@Getter
public class CurriculumStepResponse {

    private final int step;
    private final String title;
    private final String desc;

    public CurriculumStepResponse(int step, String title, String desc) {
        this.step = step;
        this.title = title;
        this.desc = desc;
    }
}
