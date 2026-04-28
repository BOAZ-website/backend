package com.boaz.backend.domain.recruitment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class QuestionIdResponse {

    @Schema(description = "질문 ID", example = "1")
    private final Long questionId;

    private QuestionIdResponse(Long questionId) {
        this.questionId = questionId;
    }

    public static QuestionIdResponse of(Long questionId) {
        return new QuestionIdResponse(questionId);
    }
}
