package com.boaz.backend.domain.recruitment.dto.request;

import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class QuestionItemRequest {

    @NotBlank(message = "label은 필수입니다.")
    @Schema(description = "파일 내보내기용 column명", example = "공통1")
    private String label;

    @NotNull(message = "카테고리는 필수입니다.")
    @Schema(description = "질문 카테고리", example = "COMMON", allowableValues = {"COMMON", "ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private ApplicationQuestion.Category category;

    @NotNull(message = "타입은 필수입니다.")
    @Schema(description = "질문 타입", example = "TEXT", allowableValues = {"TEXT", "TABLE"})
    private ApplicationQuestion.Type type;

    @NotBlank(message = "질문 내용은 필수입니다.")
    @Schema(description = "질문 내용", example = "자기소개와 BOAZ에 지원한 동기를 서술해주세요.")
    private String content;

    @Schema(description = "답변 최대 글자 수 (type=TEXT일 때 필수)", example = "500", nullable = true)
    private Integer limitLength;

    @Schema(description = "행/열 정보 (type=TABLE일 때 필수)", nullable = true)
    private JsonNode metadata;

    @NotNull(message = "출력 순서는 필수입니다.")
    @Positive(message = "출력 순서는 양수여야 합니다.")
    @Schema(description = "출력 순서", example = "1")
    private Integer orderNum;

    @NotNull(message = "필수 여부는 필수입니다.")
    @Schema(description = "필수 여부", example = "true")
    private Boolean isRequired;
}
