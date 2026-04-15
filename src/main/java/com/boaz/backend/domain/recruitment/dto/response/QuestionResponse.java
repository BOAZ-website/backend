package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionResponse {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String questionId;
    private final String category;
    private final String type;
    private final String content;
    private final Integer limitLength;
    private final JsonNode metadata;
    private final Integer orderNum;
    private final Boolean isRequired;
    
    public static QuestionResponse from(ApplicationQuestion question) {
        return new QuestionResponse(question);
    }
    
    public static QuestionResponse from(ApplicationQuestion question, String content) {
        return new QuestionResponse(question, content);
    }

    private QuestionResponse(ApplicationQuestion question) {
        this(question, question.getContent());
    }
    
    private QuestionResponse(ApplicationQuestion question, String content) {
        this.questionId = question.getId();
        this.category = question.getCategory().name();
        this.type = question.getType().name();
        this.content = content;
        this.orderNum = question.getOrderNum();
        this.isRequired = question.getIsRequired();

        if (question.getType() == ApplicationQuestion.Type.TEXT) {
            this.limitLength = question.getLimitLength();
            this.metadata = null;
        } else {
            this.limitLength = null;
            if (question.getMetadata() == null) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            try {
                this.metadata = objectMapper.readTree(question.getMetadata());
            } catch (Exception e) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }
}