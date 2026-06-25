package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.recruitment.entity.ApplicantEval;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.global.common.enums.Track;
import lombok.Getter;


@Getter
public class EvaluatorEvaluationResponse {

    private final Long adminId;

    private final String name;

    private final Track track;

    private final EvaluationDecision decision;

    private final Integer score;

    private final String memo;

    private EvaluatorEvaluationResponse(Long adminId, String name, Track track,
                                        EvaluationDecision decision, Integer score, String memo) {
        this.adminId = adminId;
        this.name = name;
        this.track = track;
        this.decision = decision;
        this.score = score;
        this.memo = memo;
    }

    // 평가자(Admin) + 본인 평가(eval, 미평가 시 null)
    public static EvaluatorEvaluationResponse of(Admin admin, ApplicantEval eval) {
        return new EvaluatorEvaluationResponse(
                admin.getId(),
                admin.getName(),
                admin.getTrack(),
                eval != null ? eval.getDecision() : null,
                eval != null ? eval.getScore() : null,
                eval != null ? eval.getMemo() : null
        );
    }
}
