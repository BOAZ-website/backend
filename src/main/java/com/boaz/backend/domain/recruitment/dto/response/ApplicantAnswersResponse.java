package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ApplicantAnswersResponse {

    private final Long applicantId;

    private final List<AnswerDetailResponse> answers;

    public static ApplicantAnswersResponse of(Long applicantId, List<AnswerDetailResponse> answers) {
        return ApplicantAnswersResponse.builder()
                .applicantId(applicantId)
                .answers(answers)
                .build();
    }

    @Getter
    @Builder
    public static class AnswerDetailResponse {

        private final Long questionId;

        private final String label;

        private final ApplicationQuestion.Category category;

        private final ApplicationQuestion.Type type;

        private final String content;

        private final Integer orderNum;

        // TEXT → 문자열, TABLE → 객체 (사용자용 AnswerItemResponse와 동일 규약)
        private final JsonNode answer;
    }
}
