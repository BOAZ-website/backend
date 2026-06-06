package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionResponse {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Schema(description = "질문 ID", example = "1")
    private final Long questionId;

    @Schema(description = "질문 레이블", example = "공통1")
    private final String label;

    @Schema(description = "카테고리", example = "COMMON", allowableValues = {"COMMON", "ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private final String category;

    @Schema(description = "질문 유형", example = "TEXT", allowableValues = {"TEXT", "TABLE"})
    private final String type;

    @Schema(description = "질문 내용", example = "자신을 소개해주세요.")
    private final String content;

    @Schema(description = "질문 보조 설명", nullable = true)
    private final String description;

    @Schema(description = "글자 수 제한 (TEXT 유형인 경우)", example = "500", nullable = true)
    private final Integer limitLength;

    @Schema(description = "메타데이터 (TABLE 유형인 경우)", nullable = true)
    private final JsonNode metadata;

    @Schema(description = "질문 순서", example = "1")
    private final Integer orderNum;

    @Schema(description = "필수 여부", example = "true")
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
        this.label = question.getLabel();
        this.category = question.getCategory().name();
        this.type = question.getType().name();
        this.content = content;
        this.description = question.getDescription();
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