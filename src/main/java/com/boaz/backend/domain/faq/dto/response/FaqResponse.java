package com.boaz.backend.domain.faq.dto.response;

import com.boaz.backend.domain.faq.entity.Faq;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class FaqResponse {

    @Schema(description = "FAQ ID", example = "1")
    private final Long id;

    @Schema(description = "질문", example = "BOAZ에 지원하려면 어떤 조건이 필요한가요?")
    private final String question;

    @Schema(description = "답변", example = "학부생 및 대학원생이라면 누구나 지원 가능합니다. 전공 제한 없이 데이터에 관심 있는 분이라면 환영합니다.")
    private final String answer;

    @Schema(description = "카테고리", example = "지원", allowableValues = {"RECRUITMENT", "ACTIVITY", "ETC"})
    private final String category;

    @JsonProperty("order_num")
    @Schema(description = "정렬 순서", example = "1")
    private final Integer orderNum;

    private FaqResponse(Long id, String question, String answer, String category, Integer orderNum) {
        this.id = id;
        this.question = question;
        this.answer = answer;
        this.category = category;
        this.orderNum = orderNum;
    }

    public static FaqResponse from(Faq faq) {
        return new FaqResponse(
                faq.getId(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getCategory().name(),
                faq.getOrderNum()
        );
    }
}
