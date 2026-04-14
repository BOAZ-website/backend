package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Tag(name = "[Admin] Recruitment", description = "Admin 전용 모집 공고 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/recruitment")
public class RecruitmentAdminController {
    
    private final RecruitmentService recruitmentService;

    @Operation(summary = "지원서 CSV 파일 생성")
    @PostMapping("/applications/download")
    public ResponseEntity<ApiResponse<Void>> downloadApplications(
            @RequestParam Integer term) {
        recruitmentService.downloadApplications(term);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
