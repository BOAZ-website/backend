package com.boaz.backend.domain.recruitment.dto.response;

import lombok.Getter;

import java.util.List;


@Getter
public class ApplicantInterviewQuestionsResponse {

    private final Long applicantId;

    private final List<EvaluatorInterviewQuestionResponse> interviewQuestions;

    private ApplicantInterviewQuestionsResponse(Long applicantId,
                                                List<EvaluatorInterviewQuestionResponse> interviewQuestions) {
        this.applicantId = applicantId;
        this.interviewQuestions = interviewQuestions;
    }

    public static ApplicantInterviewQuestionsResponse of(
            Long applicantId, List<EvaluatorInterviewQuestionResponse> interviewQuestions) {
        return new ApplicantInterviewQuestionsResponse(applicantId, interviewQuestions);
    }
}
