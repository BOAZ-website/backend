package com.boaz.backend.domain.faq.dto;

import com.boaz.backend.domain.faq.entity.Faq;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class FaqResponse {

    private final Long id;
    private final String question;
    private final String answer;
    private final String category;

    @JsonProperty("order_num")
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
