package com.boaz.backend.domain.admin.controller;

import com.boaz.backend.domain.admin.dto.request.AdminCreateRequest;
import com.boaz.backend.domain.admin.dto.request.AdminUpdateRequest;
import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.dto.response.AdminIdResponse;
import com.boaz.backend.domain.admin.service.AdminService;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.security.AdminUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(summary = "계정 생성 (SUPER only)")
    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<AdminIdResponse>> createAccount(
            @RequestBody @Valid AdminCreateRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails
    ) {
        AdminIdResponse response = adminService.createAccount(request, userDetails.getAdmin());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

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

    @Operation(summary = "id별 계정 수정 (SUPER: role 포함 전체 / TEAM: 본인 프로필만)")
    @PatchMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<AdminIdResponse>> updateAccount(
            @PathVariable Long id,
            @RequestBody AdminUpdateRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails
    ) {
        AdminIdResponse response = adminService.updateAccount(id, request, userDetails.getAdmin());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}