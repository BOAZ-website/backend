package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.ApplicantEval;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import lombok.Getter;


@Getter
public class MyEvaluationResponse {

    private final Long evaluationId;

    private final Long applicantId;

    private final EvaluationDecision decision;

    private final Integer score;

    private final String memo;

    private MyEvaluationResponse(Long evaluationId, Long applicantId,
                                EvaluationDecision decision, Integer score, String memo) {
        this.evaluationId = evaluationId;
        this.applicantId = applicantId;
        this.decision = decision;
        this.score = score;
        this.memo = memo;
    }

    public static MyEvaluationResponse from(ApplicantEval eval) {
        return new MyEvaluationResponse(
                eval.getId(),
                eval.getApplicant().getId(),
                eval.getDecision(),
                eval.getScore(),
                eval.getMemo()
        );
    }
}
