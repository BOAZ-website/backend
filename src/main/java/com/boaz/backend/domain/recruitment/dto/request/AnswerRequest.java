package com.boaz.backend.domain.recruitment.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AnswerRequest {

    @NotNull(message = "question_id를 입력해주세요.")
    @JsonProperty("question_id")
    private Long questionId;

    @NotNull(message = "답변을 입력해주세요.")
    private JsonNode answer;
}