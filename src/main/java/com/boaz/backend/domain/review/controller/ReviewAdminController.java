package com.boaz.backend.domain.review.controller;

import com.boaz.backend.domain.review.dto.request.ReviewCreateRequest;
import com.boaz.backend.domain.review.dto.request.ReviewUpdateRequest;
import com.boaz.backend.domain.review.dto.response.ReviewResponse;
import com.boaz.backend.domain.review.service.ReviewService;
import com.boaz.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[Admin] Review", description = "Admin 전용 수료자 후기 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reviews")
public class ReviewAdminController {

    private final ReviewService reviewService;

    @Operation(summary = "수료자 후기 등록", description = "수료자의 동아리 활동 후기를 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @RequestBody @Valid ReviewCreateRequest request) {
        ReviewResponse response = reviewService.createReview(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "수료자 후기 수정", description = "등록된 수료자 후기를 부분 수정합니다. (PATCH)")
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable Long reviewId,
            @RequestBody @Valid ReviewUpdateRequest request) {
        ReviewResponse response = reviewService.updateReview(reviewId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "수료자 후기 삭제", description = "등록된 수료자 후기를 삭제합니다.")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
