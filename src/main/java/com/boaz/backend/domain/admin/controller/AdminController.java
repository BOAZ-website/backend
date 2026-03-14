package com.boaz.backend.domain.admin.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Value("${admin.api-key}")
    private String adminKeyValue;
    private final RecruitmentService recruitmentService;

    @Operation(summary = "지원서 CSV 파일 생성")
    @PostMapping("/recruitment/applications/download")
    public ResponseEntity<ApiResponse<Void>> downloadApplications(
            @RequestParam Integer term,
            @RequestHeader("X-Admin-Key") String adminKey) {
        if (!adminKey.equals(adminKeyValue)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        recruitmentService.downloadApplications(term);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}