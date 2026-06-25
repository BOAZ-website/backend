package com.boaz.backend.domain.recruitment.dto.request;

import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FinalDecisionUpdateRequest {

    @NotNull
    private EvaluationDecision finalDecision;
}
