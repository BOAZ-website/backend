package com.boaz.backend.domain.curriculum.dto;

import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class CurriculumResponse {

    private final Long id;
    private final String track;

    @JsonProperty("curriculum_steps")
    private final List<CurriculumStepResponse> curriculumSteps;

    private CurriculumResponse(Long id, String track, List<CurriculumStepResponse> curriculumSteps) {
        this.id = id;
        this.track = track;
        this.curriculumSteps = curriculumSteps;
    }

    public static CurriculumResponse from(Curriculum curriculum, List<CurriculumStepResponse> steps) {
        return new CurriculumResponse(
                curriculum.getId(),
                curriculum.getTrack().name(),
                steps
        );
    }
}
