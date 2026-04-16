package com.boaz.backend.domain.curriculum.dto.request;

import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class CurriculumCreateRequest {

    @NotNull(message = "트랙은 필수입니다.")
    @Schema(description = "트랙", example = "ANALYSIS", allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private Track track;

    @NotNull(message = "커리큘럼 단계 목록은 필수입니다.")
    @NotEmpty(message = "커리큘럼 단계는 1개 이상이어야 합니다.")
    @Valid
    @Schema(description = "커리큘럼 단계 목록")
    private List<CurriculumStepRequest> curriculumSteps;
}
