package com.boaz.backend.domain.faq.dto.request;

import com.boaz.backend.domain.faq.entity.Faq;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class FaqUpdateRequest {

    @Schema(description = "질문", example = "BOAZ에 지원하려면 어떤 조건이 필요한가요?", nullable = true)
    private String question;

    @Schema(description = "답변", example = "학부생 및 대학원생이라면 누구나 지원 가능합니다.", nullable = true)
    private String answer;

    @Schema(description = "카테고리", example = "RECRUITMENT", allowableValues = {"RECRUITMENT", "ACTIVITY", "ETC"}, nullable = true)
    private Faq.Category category;

    @Positive(message = "순서 번호는 양의 정수여야 합니다.")
    @Schema(description = "정렬 순서", example = "1", nullable = true)
    private Integer orderNum;
}
