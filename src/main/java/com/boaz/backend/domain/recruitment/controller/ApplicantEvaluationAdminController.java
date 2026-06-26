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
            description = "공고 내 전체 지원서를 반환합니다. DRAFT 포함, 정렬·필터·검색은 프론트에서 처리. 차기대표진은 전 부문, 그 외(현재 대표진 포함)는 본인 부문만 조회.")
    @GetMapping("/{recruitmentId}/applicants")
    public ResponseEntity<ApiResponse<List<ApplicantSummaryResponse>>> getApplicants(
            @PathVariable Long recruitmentId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicants(recruitmentId, userDetails.getAdmin())));
    }

    @Operation(summary = "전체 지원서 및 평가 조회 (평가 대시보드)",
            description = "공고 내 SUBMITTED 지원서 + 평가 집계(합격/보류/불합 개수·총점) + 최종 평가를 반환합니다. 차기대표진은 전 부문, 그 외(현재 대표진 포함)는 본인 부문만 조회.")
    @GetMapping("/{recruitmentId}/applicants/evaluations")
    public ResponseEntity<ApiResponse<List<ApplicantEvaluationResponse>>> getApplicantEvaluations(
            @PathVariable Long recruitmentId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicantEvaluations(recruitmentId, userDetails.getAdmin())));
    }

    @Operation(summary = "최종 평가 수정 (대표진 전용)",
            description = "현재 대표진 또는 차기대표진(role=SUPER)만 지원자의 최종 평가(final_decision)를 수정합니다. 현재 대표진은 본인 부문만, 차기대표진은 전 부문.")
    @PatchMapping("/applicants/{applicantId}/final-decision")
    public ResponseEntity<ApiResponse<FinalDecisionResponse>> updateFinalDecision(
            @PathVariable Long applicantId,
            @RequestBody @Valid FinalDecisionUpdateRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.updateFinalDecision(applicantId, request, userDetails.getAdmin())));
    }

    @Operation(summary = "지원서별 평가 조회",
            description = "한 지원자에 대한 해당 부문 평가자 전체의 평가를 반환합니다. 미평가 평가자는 null로 포함. 차기대표진 외(현재 대표진 포함)에는 본인 부문 지원자만 조회.")
    @GetMapping("/applicants/{applicantId}/evaluations")
    public ResponseEntity<ApiResponse<ApplicantEvaluatorsResponse>> getApplicantEvaluators(
            @PathVariable Long applicantId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicantEvaluators(applicantId, userDetails.getAdmin())));
    }

    @Operation(summary = "지원서별 면접 질문 조회",
            description = "한 지원자에 대한 해당 부문 평가자별 면접 질문을 반환합니다. 미작성 평가자는 null로 포함. 차기대표진 외(현재 대표진 포함)에는 본인 부문 지원자만 조회.")
    @GetMapping("/applicants/{applicantId}/interview-questions")
    public ResponseEntity<ApiResponse<ApplicantInterviewQuestionsResponse>> getApplicantInterviewQuestions(
            @PathVariable Long applicantId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicantInterviewQuestions(applicantId, userDetails.getAdmin())));
    }

    @Operation(summary = "지원서 답변 조회",
            description = "한 지원자가 작성한 문항별 답변을 문항 정보(질문 내용·유형)와 함께 문항 순서대로 반환합니다. (평가 사이드바 지원서 본문) 차기대표진 외(현재 대표진 포함)에는 본인 부문 지원자만 조회.")
    @GetMapping("/applicants/{applicantId}/answers")
    public ResponseEntity<ApiResponse<ApplicantAnswersResponse>> getApplicantAnswers(
            @PathVariable Long applicantId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicantAnswers(applicantId, userDetails.getAdmin())));
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
