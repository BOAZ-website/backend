package com.boaz.backend.domain.curriculum.dto.response;

import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
public class CurriculumResponse {

    @Schema(description = "커리큘럼 ID", example = "1")
    private final Long id;

    @Schema(description = "트랙", example = "ANALYSIS", allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private final String track;

    @JsonProperty("curriculum_steps")
    @Schema(description = "커리큘럼 단계 목록")
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
