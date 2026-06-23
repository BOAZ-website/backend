package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.recruitment.entity.ApplicantEval;
import com.boaz.backend.global.common.enums.Track;
import lombok.Getter;


@Getter
public class EvaluatorInterviewQuestionResponse {

    private final Long adminId;

    private final String name;

    private final Track track;

    private final String interviewQuestion;

    private EvaluatorInterviewQuestionResponse(Long adminId, String name, Track track,
                                               String interviewQuestion) {
        this.adminId = adminId;
        this.name = name;
        this.track = track;
        this.interviewQuestion = interviewQuestion;
    }

    // 평가자(Admin) + 본인 평가(eval, 미작성 시 null)
    public static EvaluatorInterviewQuestionResponse of(Admin admin, ApplicantEval eval) {
        return new EvaluatorInterviewQuestionResponse(
                admin.getId(),
                admin.getName(),
                admin.getTrack(),
                eval != null ? eval.getInterviewQuestion() : null
        );
    }
}
