package com.boaz.backend.domain.recruitment.dto;

import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionResponse {

    private final String questionId;
    private final String category;
    private final String type;
    private final String content;
    private final Integer limitLength;
    private final JsonNode metadata;
    private final Integer orderNum;
    private final Boolean isRequired;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private QuestionResponse(ApplicationQuestion question) {
        this.questionId = question.getId();
        this.category = question.getCategory().name();
        this.type = question.getType().name();
        this.content = question.getContent();
        this.orderNum = question.getOrderNum();
        this.isRequired = question.getIsRequired();

        if (question.getType() == QuestionType.TEXT) {
            this.limitLength = question.getLimitLength();
            this.metadata = null;
        } else {
            this.limitLength = null;
            try {
                this.metadata = objectMapper.readTree(question.getMetadata());
            } catch (Exception e) {
                throw new RuntimeException("metadata JSON 파싱 실패: " + question.getId());
            }
        }
    }

    public static QuestionResponse from(ApplicationQuestion question) {
        return new QuestionResponse(question);
    }
}