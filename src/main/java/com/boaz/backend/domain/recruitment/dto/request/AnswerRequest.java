package com.boaz.backend.domain.recruitment.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AnswerRequest {

    @Schema(description = "질문 ID", example = "1")
    @NotNull(message = "question_id를 입력해주세요.")
    @JsonProperty("question_id")
    private Long questionId;

    @Schema(description = "답변")
    @NotNull(message = "답변을 입력해주세요.")
    private JsonNode answer;
}