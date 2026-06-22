package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.recruitment.dto.request.EvaluationSaveRequest;
import com.boaz.backend.domain.recruitment.dto.request.FinalDecisionUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.response.*;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.security.AdminUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "[Admin] Applicant Evaluation", description = "Admin 전용 지원서 평가 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/recruitment")
public class ApplicantEvaluationAdminController {

    private final RecruitmentService recruitmentService;

    @Operation(summary = "전체 지원서 조회 (지원자 대시보드)",
            description = "공고 내 전체 지원서를 반환합니다. DRAFT 포함, 정렬·필터·검색은 프론트에서 처리.")
    @GetMapping("/{recruitmentId}/applicants")
    public ResponseEntity<ApiResponse<List<ApplicantSummaryResponse>>> getApplicants(
            @PathVariable Long recruitmentId) {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.getApplicants(recruitmentId)));
    }

    @Operation(summary = "전체 지원서 및 평가 조회 (평가 대시보드)",
            description = "공고 내 SUBMITTED 지원서 + 평가 집계(합격/보류/불합 개수·총점) + 최종 평가를 반환합니다.")
    @GetMapping("/{recruitmentId}/applicants/evaluations")
    public ResponseEntity<ApiResponse<List<ApplicantEvaluationResponse>>> getApplicantEvaluations(
            @PathVariable Long recruitmentId) {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.getApplicantEvaluations(recruitmentId)));
    }

    @Operation(summary = "최종 평가 수정 (대표진 전용)",
            description = "대표진(SUPER & teamName=대표진)만 지원자의 최종 평가(final_decision)를 수정합니다.")
    @PatchMapping("/applicants/{applicantId}/final-decision")
    public ResponseEntity<ApiResponse<FinalDecisionResponse>> updateFinalDecision(
            @PathVariable Long applicantId,
            @RequestBody @Valid FinalDecisionUpdateRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.updateFinalDecision(applicantId, request, userDetails.getAdmin())));
    }

    @Operation(summary = "지원서별 평가 조회",
            description = "한 지원자에 대한 해당 부문 평가자 전체의 평가를 반환합니다. 미평가 평가자는 null로 포함.")
    @GetMapping("/applicants/{applicantId}/evaluations")
    public ResponseEntity<ApiResponse<ApplicantEvaluatorsResponse>> getApplicantEvaluators(
            @PathVariable Long applicantId) {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.getApplicantEvaluators(applicantId)));
    }

    @Operation(summary = "개인 평가 조회",
            description = "로그인한 평가자 본인이 이 지원자에 매긴 평가를 반환합니다. 미평가 시 data=null.")
    @GetMapping("/applicants/{applicantId}/evaluations/me")
    public ResponseEntity<ApiResponse<MyEvaluationResponse>> getMyEvaluation(
            @PathVariable Long applicantId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getMyEvaluation(applicantId, userDetails.getAdmin())));
    }

    @Operation(summary = "개인 평가 저장 (upsert)",
            description = "로그인한 평가자 본인의 평가를 저장합니다. 본인 부문 지원자만 가능, SUBMITTED만 가능.")
    @PutMapping("/applicants/{applicantId}/evaluations/me")
    public ResponseEntity<ApiResponse<MyEvaluationResponse>> saveMyEvaluation(
            @PathVariable Long applicantId,
            @RequestBody @Valid EvaluationSaveRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.saveMyEvaluation(applicantId, request, userDetails.getAdmin())));
    }
}
