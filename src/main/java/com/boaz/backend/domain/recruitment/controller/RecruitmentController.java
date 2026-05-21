package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.recruitment.dto.request.ApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.DraftApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.SubscriptionRequest;
import com.boaz.backend.domain.recruitment.dto.response.ApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.DeadlineResponse;
import com.boaz.backend.domain.recruitment.dto.response.DraftApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.MyApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.QuestionResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentStatusResponse;
import com.boaz.backend.domain.recruitment.dto.response.SubscriptionResponse;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Recruitment", description = "모집 공고 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment")
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    @Operation(summary = "모집 중 여부 조회")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<RecruitmentStatusResponse>> getRecruitmentStatus() {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.getRecruitmentStatus()));
    }

    @Operation(summary = "모집 마감 일시 조회")
    @GetMapping("/deadline")
    public ResponseEntity<ApiResponse<DeadlineResponse>> getDeadline() {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.getDeadline()));
    }

    @Operation(summary = "모집 공고 정보 조회")
    @GetMapping("/{term}")
    public ResponseEntity<ApiResponse<RecruitmentResponse>> getRecruitment(@PathVariable Integer term) {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.getRecruitment(term)));
    }

    @Operation(summary = "지원서 질문 목록 조회")
    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getQuestions(
            @RequestParam Long recruitmentId,
            @RequestParam Track track) {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.getQuestions(recruitmentId, track)));
    }

    @Operation(summary = "지원서 제출")
    @PostMapping("/{recruitmentId}/applications")
    public ResponseEntity<ApiResponse<ApplicationResponse>> submitApplication(
            @PathVariable Long recruitmentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid ApplicationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        recruitmentService.submitApplication(principal.userId(), recruitmentId, request)));
    }

    @Operation(summary = "지원서 임시저장")
    @PutMapping("/{recruitmentId}/applications/draft")
    public ResponseEntity<ApiResponse<DraftApplicationResponse>> saveDraft(
            @PathVariable Long recruitmentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody DraftApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.saveDraft(principal.userId(), recruitmentId, request)));
    }

    @Operation(summary = "내 지원서 조회")
    @GetMapping("/{recruitmentId}/applications/me")
    public ResponseEntity<ApiResponse<MyApplicationResponse>> getMyApplication(
            @PathVariable Long recruitmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                recruitmentService.getMyApplication(principal.userId(), recruitmentId)));
    }

    @Operation(summary = "모집 사전 알림 구독")
    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> subscribe(
            @RequestBody @Valid SubscriptionRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(recruitmentService.subscribe(request)));
    }
}
