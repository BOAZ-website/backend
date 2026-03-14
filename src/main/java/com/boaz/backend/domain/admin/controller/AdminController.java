package com.boaz.backend.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final RecruitmentService recruitmentService;

    @Operation(summary = "지원서 CSV 파일 생성")
    @PostMapping("/recruitment/applications/download")
    public ResponseEntity<ApiResponse<Void>> downloadApplications(
            @RequestParam Integer term) {
        recruitmentService.downloadApplications(term);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}