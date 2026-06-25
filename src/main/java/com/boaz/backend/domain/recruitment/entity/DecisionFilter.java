package com.boaz.backend.domain.recruitment.entity;

import io.swagger.v3.oas.annotations.media.Schema;

// 지원서 CSV 추출 시 final_decision 기준 추출 범위 필터 (서류 평가 결과 전달용)
public enum DecisionFilter {
    @Schema(description = "합격자만")
    PASS,
    @Schema(description = "불합격자만")
    FAIL,
    @Schema(description = "전체 (필터 없음, 기본값)")
    ALL;

    // 필터에 대응하는 EvaluationDecision 반환. ALL이면 null(필터 미적용)
    public EvaluationDecision toEvaluationDecisionOrNull() {
        return this == ALL ? null : EvaluationDecision.valueOf(name());
    }
}
