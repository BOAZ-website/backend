package com.boaz.backend.domain.admin.controller;

import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.service.AdminService;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.security.AdminUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin", description = "관리자 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "모든 계정 조회 (SUPER only)")
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AdminAccountResponse>>> getAccounts(
            @AuthenticationPrincipal AdminUserDetails userDetails
    ) {
        List<AdminAccountResponse> response = adminService.getAccounts(userDetails.getAdmin());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "id별 계정 조회 (SUPER: 전체 / TEAM: 본인만)")
    @GetMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<AdminAccountResponse>> getAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal AdminUserDetails userDetails
    ) {
        AdminAccountResponse response = adminService.getAccount(id, userDetails.getAdmin());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}