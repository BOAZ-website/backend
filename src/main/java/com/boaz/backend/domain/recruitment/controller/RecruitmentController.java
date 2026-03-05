package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.recruitment.dto.RecruitmentResponse;
import com.boaz.backend.domain.recruitment.dto.RecruitmentStatusResponse;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "모집 공고 정보 조회")
    @GetMapping("/{term}")
    public ResponseEntity<ApiResponse<RecruitmentResponse>> getRecruitment(@PathVariable Integer term) {
        return ResponseEntity.ok(ApiResponse.ok(recruitmentService.getRecruitment(term)));
    }
}