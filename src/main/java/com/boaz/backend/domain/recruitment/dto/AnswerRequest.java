package com.boaz.backend.domain.recruitment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AnswerRequest {

    @NotNull
    @JsonProperty("question_id")
    private String questionId;

    @NotNull
    private JsonNode answer;
}