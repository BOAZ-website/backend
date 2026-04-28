package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.recruitment.dto.request.QuestionsCreateRequest;
import com.boaz.backend.domain.recruitment.dto.request.QuestionUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.response.QuestionIdResponse;
import com.boaz.backend.domain.recruitment.dto.response.QuestionIdsResponse;
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

@Tag(name = "[Admin] Question", description = "Admin 전용 지원서 질문 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/questions")
public class QuestionAdminController {

    private final RecruitmentService recruitmentService;

    @Operation(summary = "지원서 질문 등록", description = "모집 공고에 지원서 질문을 다건 등록합니다. 하나라도 실패 시 전체 롤백.")
    @PostMapping
    public ResponseEntity<ApiResponse<QuestionIdsResponse>> createQuestions(
            @RequestBody @Valid QuestionsCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(recruitmentService.createQuestions(request)));
    }

    @Operation(summary = "지원서 질문 수정", description = "등록된 지원서 질문을 부분 수정합니다. type 변경 시 연관 필드 함께 처리.")
    @PatchMapping("/{questionId}")
    public ResponseEntity<ApiResponse<QuestionIdResponse>> updateQuestion(
            @PathVariable Long questionId,
            @RequestBody QuestionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.updateQuestion(questionId, request)));
    }

    @Operation(summary = "지원서 질문 삭제", description = "등록된 지원서 질문을 삭제합니다. 참조 답변 데이터 존재 시 삭제 불가.")
    @DeleteMapping("/{questionId}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable Long questionId) {
        recruitmentService.deleteQuestion(questionId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
