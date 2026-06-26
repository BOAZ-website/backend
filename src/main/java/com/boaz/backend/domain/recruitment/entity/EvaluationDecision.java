package com.boaz.backend.domain.recruitment.entity;

import io.swagger.v3.oas.annotations.media.Schema;


public enum EvaluationDecision {
    @Schema(description = "합격")
    PASS,
    @Schema(description = "불합격")
    FAIL,
    @Schema(description = "보류")
    HOLD,
    @Schema(description = "미정 (기본값)")
    PENDING
}