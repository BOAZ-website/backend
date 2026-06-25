package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import lombok.Getter;


@Getter
public class FinalDecisionResponse {

    private final Long applicantId;

    private final EvaluationDecision finalDecision;

    private FinalDecisionResponse(Long applicantId, EvaluationDecision finalDecision) {
        this.applicantId = applicantId;
        this.finalDecision = finalDecision;
    }

    public static FinalDecisionResponse from(Applicant applicant) {
        return new FinalDecisionResponse(applicant.getId(), applicant.getFinalDecision());
    }
}
