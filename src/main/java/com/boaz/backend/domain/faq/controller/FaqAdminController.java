package com.boaz.backend.domain.faq.controller;

import com.boaz.backend.domain.faq.dto.request.FaqCreateRequest;
import com.boaz.backend.domain.faq.dto.request.FaqUpdateRequest;
import com.boaz.backend.domain.faq.dto.response.FaqResponse;
import com.boaz.backend.domain.faq.service.FaqService;
import com.boaz.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[Admin] FAQ", description = "Admin 전용 FAQ API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/faqs")
public class FaqAdminController {

    private final FaqService faqService;

    @Operation(summary = "FAQ 등록", description = "카테고리별 FAQ를 등록합니다. 동일 카테고리 내 순서 번호 중복 불가.")
    @PostMapping
    public ResponseEntity<ApiResponse<FaqResponse>> createFaq(
            @RequestBody @Valid FaqCreateRequest request) {
        FaqResponse response = faqService.createFaq(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "FAQ 수정", description = "등록된 FAQ를 부분 수정합니다. (PATCH)")
    @PatchMapping("/{faqId}")
    public ResponseEntity<ApiResponse<FaqResponse>> updateFaq(
            @PathVariable Long faqId,
            @RequestBody @Valid FaqUpdateRequest request) {
        FaqResponse response = faqService.updateFaq(faqId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "FAQ 삭제", description = "등록된 FAQ를 삭제합니다.")
    @DeleteMapping("/{faqId}")
    public ResponseEntity<ApiResponse<Void>> deleteFaq(
            @PathVariable Long faqId) {
        faqService.deleteFaq(faqId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
