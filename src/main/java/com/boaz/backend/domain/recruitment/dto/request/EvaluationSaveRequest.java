package com.boaz.backend.domain.recruitment.dto.request;

import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@NoArgsConstructor
public class EvaluationSaveRequest {

    @NotNull
    private EvaluationDecision decision;

    // 1~10 또는 null (미점수). null이면 검증 통과
    @Min(1)
    @Max(10)
    private Integer score;

    private String memo;
}
