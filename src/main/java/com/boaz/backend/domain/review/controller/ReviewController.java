package com.boaz.backend.domain.review.controller;

import com.boaz.backend.domain.review.dto.response.ReviewResponse;
import com.boaz.backend.domain.review.service.ReviewService;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.common.enums.Track;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
@Tag(name = "Review", description = "후기 API")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    @Operation(summary = "후기 전체 조회", description = "부문/기수 필터링 가능. 기본 정렬은 기수 내림차순.")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviews(
            @RequestParam(required = false) Track track,
            @RequestParam(required = false) Integer term) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getReviews(track, term)));
    }
}

