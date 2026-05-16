package com.boaz.backend.domain.curriculum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class CurriculumUpdateRequest {

    @NotNull(message = "커리큘럼 단계 목록은 필수입니다.")
    @NotEmpty(message = "커리큘럼 단계는 1개 이상이어야 합니다.")
    @Valid
    @Schema(description = "커리큘럼 단계 목록 (전체 교체)")
    private List<CurriculumStepRequest> curriculumSteps;
}
