package com.boaz.backend.domain.recruitment.dto.response;

import lombok.Getter;

import java.util.List;


@Getter
public class ApplicantEvaluatorsResponse {

    private final Long applicantId;

    private final List<EvaluatorEvaluationResponse> evaluations;

    private ApplicantEvaluatorsResponse(Long applicantId, List<EvaluatorEvaluationResponse> evaluations) {
        this.applicantId = applicantId;
        this.evaluations = evaluations;
    }

    public static ApplicantEvaluatorsResponse of(Long applicantId, List<EvaluatorEvaluationResponse> evaluations) {
        return new ApplicantEvaluatorsResponse(applicantId, evaluations);
    }
}
