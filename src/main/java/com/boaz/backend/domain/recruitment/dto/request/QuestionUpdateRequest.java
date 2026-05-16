package com.boaz.backend.domain.recruitment.dto.request;

import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import org.openapitools.jackson.nullable.JsonNullable;

@Getter
public class QuestionUpdateRequest {

    @Schema(description = "파일 내보내기용 column명", example = "공통1", nullable = true)
    private String label;

    @Schema(description = "질문 카테고리", example = "COMMON", allowableValues = {"COMMON", "ANALYSIS", "VISUALIZATION", "ENGINEERING"}, nullable = true)
    private ApplicationQuestion.Category category;

    @Schema(description = "질문 타입", example = "TEXT", allowableValues = {"TEXT", "TABLE"}, nullable = true)
    private ApplicationQuestion.Type type;

    @Schema(description = "질문 내용", example = "자기소개와 BOAZ에 지원한 동기를 서술해주세요.", nullable = true)
    private String content;

    @Schema(description = "답변 최대 글자 수 (null 전송 시 삭제)", example = "500", nullable = true)
    private JsonNullable<Integer> limitLength = JsonNullable.undefined();

    @Schema(description = "행/열 정보 (null 전송 시 삭제)", nullable = true)
    private JsonNullable<JsonNode> metadata = JsonNullable.undefined();

    @Positive(message = "출력 순서는 양수여야 합니다.")
    @Schema(description = "출력 순서", example = "1", nullable = true)
    private Integer orderNum;

    @Schema(description = "필수 여부", example = "true", nullable = true)
    private Boolean isRequired;
}
