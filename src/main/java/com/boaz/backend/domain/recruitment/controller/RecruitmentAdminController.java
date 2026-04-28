package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.recruitment.dto.request.QuestionsCreateRequest;
import com.boaz.backend.domain.recruitment.dto.request.QuestionUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.request.RecruitmentCreateRequest;
import com.boaz.backend.domain.recruitment.dto.request.RecruitmentUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.response.QuestionIdResponse;
import com.boaz.backend.domain.recruitment.dto.response.QuestionIdsResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentIdResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentResponse;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "[Admin] Recruitment", description = "Admin 전용 모집 공고 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/recruitment")
public class RecruitmentAdminController {

    private final RecruitmentService recruitmentService;

    @Operation(summary = "모든 모집 공고 조회", description = "is_active 무관 전체 공고를 term 내림차순으로 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RecruitmentResponse>>> getAllRecruitments() {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.getAllRecruitments()));
    }

    @Operation(summary = "모집 공고 등록", description = "새로운 모집 공고를 등록합니다. 동일 기수 중복 등록 불가.")
    @PostMapping
    public ResponseEntity<ApiResponse<RecruitmentIdResponse>> createRecruitment(
            @RequestBody @Valid RecruitmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(recruitmentService.createRecruitment(request)));
    }

    @Operation(summary = "모집 공고 수정", description = "특정 모집 공고를 부분 수정합니다. schedule 수정 시 배열 전체 교체.")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<RecruitmentIdResponse>> updateRecruitment(
            @PathVariable Long id,
            @RequestBody RecruitmentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.updateRecruitment(id, request)));
    }

    @Operation(summary = "모집 공고 삭제", description = "특정 모집 공고를 삭제합니다. 연관 데이터(지원자, 질문) 존재 시 삭제 불가.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRecruitment(
            @PathVariable Long id) {
        recruitmentService.deleteRecruitment(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "지원서 CSV 파일 생성")
    @PostMapping("/applications/download")
    public ResponseEntity<ApiResponse<Void>> downloadApplications(
            @RequestParam Integer term) {
        recruitmentService.downloadApplications(term);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "지원서 질문 등록", description = "모집 공고에 지원서 질문을 다건 등록합니다. 하나라도 실패 시 전체 롤백.")
    @PostMapping("/questions")
    public ResponseEntity<ApiResponse<QuestionIdsResponse>> createQuestions(
            @RequestBody @Valid QuestionsCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(recruitmentService.createQuestions(request)));
    }

    @Operation(summary = "지원서 질문 수정", description = "등록된 지원서 질문을 부분 수정합니다. type 변경 시 연관 필드 함께 처리.")
    @PatchMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<QuestionIdResponse>> updateQuestion(
            @PathVariable Long questionId,
            @RequestBody QuestionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.updateQuestion(questionId, request)));
    }

    @Operation(summary = "지원서 질문 삭제", description = "등록된 지원서 질문을 삭제합니다. 참조 답변 데이터 존재 시 삭제 불가.")
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable Long questionId) {
        recruitmentService.deleteQuestion(questionId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
