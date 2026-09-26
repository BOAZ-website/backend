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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "[Admin] Applicant Evaluation", description = "Admin 전용 지원서 평가 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/recruitment")
public class ApplicantEvaluationAdminController {

    // 기능 접근은 여기(@PreAuthorize)서, 대상 지원자의 부문 범위는 서비스의 ScopeGuard 가 본다.
    // 평가 권한은 본인 부문(OWN)·전 부문(ALL) 둘 다 같은 엔드포인트를 통과시키고 3층에서 가른다.
    // permission 이름에 ROLE_ 접두사가 없어 hasRole 이 아니라 hasAuthority 다.
    // 문자열 오타는 컴파일 타임에 안 잡힌다. ApplicantEvaluationPermissionGridTest 가 사실상 컴파일러 역할을 한다.

    private final RecruitmentService recruitmentService;

    @Operation(summary = "전체 지원서 조회 (지원자 대시보드)",
            description = "EVALUATION_OWN_TRACK_WRITE 또는 EVALUATION_ALL_TRACK_WRITE 보유자만 호출 가능. "
                    + "공고 내 전체 지원서를 반환합니다. DRAFT 포함, 정렬·필터·검색은 프론트에서 처리. "
                    + "EVALUATION_ALL_TRACK_WRITE 보유 시 전 부문, 그 외에는 본인 부문 지원서만 반환.")
    @PreAuthorize("hasAnyAuthority('EVALUATION_OWN_TRACK_WRITE','EVALUATION_ALL_TRACK_WRITE')")
    @GetMapping("/{recruitmentId}/applicants")
    public ResponseEntity<ApiResponse<List<ApplicantSummaryResponse>>> getApplicants(
            @PathVariable Long recruitmentId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicants(recruitmentId, userDetails.getAdmin(), userDetails.getPermissions())));
    }

    @Operation(summary = "전체 지원서 및 평가 조회 (평가 대시보드)",
            description = "(EVALUATION_OWN_TRACK_WRITE 또는 EVALUATION_ALL_TRACK_WRITE) 및 FINAL_DECISION_READ 보유자만 호출 가능. "
                    + "공고 내 SUBMITTED 지원서 + 평가 집계(합격/보류/불합 개수·총점) + 최종 평가를 반환합니다. "
                    + "EVALUATION_ALL_TRACK_WRITE 보유 시 전 부문, 그 외에는 본인 부문 지원서만 반환.")
    // 응답에 최종 평가(final_decision)가 실리므로 최종 합불 조회 권한까지 요구한다.
    @PreAuthorize("hasAnyAuthority('EVALUATION_OWN_TRACK_WRITE','EVALUATION_ALL_TRACK_WRITE')"
            + " and hasAuthority('FINAL_DECISION_READ')")
    @GetMapping("/{recruitmentId}/applicants/evaluations")
    public ResponseEntity<ApiResponse<List<ApplicantEvaluationResponse>>> getApplicantEvaluations(
            @PathVariable Long recruitmentId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicantEvaluations(recruitmentId, userDetails.getAdmin(), userDetails.getPermissions())));
    }

    @Operation(summary = "최종 평가 수정",
            description = "FINAL_DECISION_WRITE 보유자만 호출 가능. 지원자의 최종 평가(final_decision)를 수정합니다. "
                    + "EVALUATION_ALL_TRACK_WRITE 보유 시 전 부문, 그 외에는 본인 부문 지원자만 수정 가능(타 부문이면 403). "
                    + "SUBMITTED 지원서만 가능.")
    @PreAuthorize("hasAuthority('FINAL_DECISION_WRITE')")
    @PatchMapping("/applicants/{applicantId}/final-decision")
    public ResponseEntity<ApiResponse<FinalDecisionResponse>> updateFinalDecision(
            @PathVariable Long applicantId,
            @RequestBody @Valid FinalDecisionUpdateRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.updateFinalDecision(applicantId, request, userDetails.getAdmin(), userDetails.getPermissions())));
    }

    @Operation(summary = "지원서별 평가 조회",
            description = "EVALUATION_OWN_TRACK_WRITE 또는 EVALUATION_ALL_TRACK_WRITE 보유자만 호출 가능. "
                    + "한 지원자에 대한 해당 부문 평가자 전체의 평가를 반환합니다. 미평가 평가자는 null로 포함. "
                    + "EVALUATION_ALL_TRACK_WRITE 미보유 시 본인 부문 지원자만 조회 가능(타 부문이면 403).")
    @PreAuthorize("hasAnyAuthority('EVALUATION_OWN_TRACK_WRITE','EVALUATION_ALL_TRACK_WRITE')")
    @GetMapping("/applicants/{applicantId}/evaluations")
    public ResponseEntity<ApiResponse<ApplicantEvaluatorsResponse>> getApplicantEvaluators(
            @PathVariable Long applicantId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicantEvaluators(applicantId, userDetails.getAdmin(), userDetails.getPermissions())));
    }

    @Operation(summary = "지원서별 면접 질문 조회",
            description = "EVALUATION_OWN_TRACK_WRITE 또는 EVALUATION_ALL_TRACK_WRITE 보유자만 호출 가능. "
                    + "한 지원자에 대한 해당 부문 평가자별 면접 질문을 반환합니다. 미작성 평가자는 null로 포함. "
                    + "EVALUATION_ALL_TRACK_WRITE 미보유 시 본인 부문 지원자만 조회 가능(타 부문이면 403).")
    @PreAuthorize("hasAnyAuthority('EVALUATION_OWN_TRACK_WRITE','EVALUATION_ALL_TRACK_WRITE')")
    @GetMapping("/applicants/{applicantId}/interview-questions")
    public ResponseEntity<ApiResponse<ApplicantInterviewQuestionsResponse>> getApplicantInterviewQuestions(
            @PathVariable Long applicantId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicantInterviewQuestions(applicantId, userDetails.getAdmin(), userDetails.getPermissions())));
    }

    @Operation(summary = "지원서 답변 조회",
            description = "EVALUATION_OWN_TRACK_WRITE 또는 EVALUATION_ALL_TRACK_WRITE 보유자만 호출 가능. "
                    + "한 지원자가 작성한 문항별 답변을 문항 정보(질문 내용·유형)와 함께 문항 순서대로 반환합니다. (평가 사이드바 지원서 본문) "
                    + "EVALUATION_ALL_TRACK_WRITE 미보유 시 본인 부문 지원자만 조회 가능(타 부문이면 403).")
    @PreAuthorize("hasAnyAuthority('EVALUATION_OWN_TRACK_WRITE','EVALUATION_ALL_TRACK_WRITE')")
    @GetMapping("/applicants/{applicantId}/answers")
    public ResponseEntity<ApiResponse<ApplicantAnswersResponse>> getApplicantAnswers(
            @PathVariable Long applicantId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getApplicantAnswers(applicantId, userDetails.getAdmin(), userDetails.getPermissions())));
    }

    @Operation(summary = "개인 평가 조회",
            description = "EVALUATION_OWN_TRACK_WRITE 또는 EVALUATION_ALL_TRACK_WRITE 보유자만 호출 가능. "
                    + "로그인한 평가자 본인이 이 지원자에 매긴 평가를 반환합니다. 미평가 시 data=null. "
                    + "EVALUATION_ALL_TRACK_WRITE 미보유 시 본인 부문 지원자만 조회 가능(타 부문이면 403).")
    @PreAuthorize("hasAnyAuthority('EVALUATION_OWN_TRACK_WRITE','EVALUATION_ALL_TRACK_WRITE')")
    @GetMapping("/applicants/{applicantId}/evaluations/me")
    public ResponseEntity<ApiResponse<MyEvaluationResponse>> getMyEvaluation(
            @PathVariable Long applicantId,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getMyEvaluation(applicantId, userDetails.getAdmin(), userDetails.getPermissions())));
    }

    @Operation(summary = "개인 평가 저장 (upsert)",
            description = "EVALUATION_OWN_TRACK_WRITE 또는 EVALUATION_ALL_TRACK_WRITE 보유자만 호출 가능. "
                    + "로그인한 평가자 본인의 평가를 저장합니다. SUBMITTED만 가능. "
                    + "EVALUATION_ALL_TRACK_WRITE 보유 시 전 부문, 그 외에는 본인 부문 지원자만 가능(타 부문이면 403).")
    @PreAuthorize("hasAnyAuthority('EVALUATION_OWN_TRACK_WRITE','EVALUATION_ALL_TRACK_WRITE')")
    @PutMapping("/applicants/{applicantId}/evaluations/me")
    public ResponseEntity<ApiResponse<MyEvaluationResponse>> saveMyEvaluation(
            @PathVariable Long applicantId,
            @RequestBody @Valid EvaluationSaveRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.saveMyEvaluation(applicantId, request, userDetails.getAdmin(), userDetails.getPermissions())));
    }
}
