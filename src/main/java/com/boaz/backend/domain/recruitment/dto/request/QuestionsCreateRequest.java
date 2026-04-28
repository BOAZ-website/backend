package com.boaz.backend.domain.recruitment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class QuestionsCreateRequest {

    @NotNull(message = "모집 공고 ID는 필수입니다.")
    @Schema(description = "모집 공고 ID", example = "12")
    private Long recruitmentId;

    @NotEmpty(message = "질문 목록은 비어있을 수 없습니다.")
    @Valid
    @Schema(description = "등록할 질문 목록")
    private List<QuestionItemRequest> questions;
}
