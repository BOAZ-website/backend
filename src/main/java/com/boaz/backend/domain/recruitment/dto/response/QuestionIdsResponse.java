package com.boaz.backend.domain.recruitment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
public class QuestionIdsResponse {

    @Schema(description = "생성된 질문 ID 목록", example = "[1, 2, 3]")
    private final List<Long> ids;

    private QuestionIdsResponse(List<Long> ids) {
        this.ids = ids;
    }

    public static QuestionIdsResponse of(List<Long> ids) {
        return new QuestionIdsResponse(ids);
    }
}
