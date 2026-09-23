package com.boaz.backend.domain.admin.controller;

import com.boaz.backend.domain.admin.dto.request.AdminCreateRequest;
import com.boaz.backend.domain.admin.dto.request.AdminPasswordResetRequest;
import com.boaz.backend.domain.admin.dto.request.AdminUpdateRequest;
import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.dto.response.AdminIdResponse;
import com.boaz.backend.domain.admin.dto.response.AdminMeResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    // 2층 — permission 보유 여부만 본다. DB 조회 없음. 대상이 누구냐(본인/타인)는 3층 ScopeGuard 몫이다.
    // hasRole 이 아니라 hasAuthority 다 — permission 이름에는 ROLE_ 접두사가 없어서, 틀리면 조용히 전부 거부된다.
    // 문자열 오타는 컴파일 타임에 안 잡힌다. AdminPermissionGridTest 가 사실상 컴파일러 역할을 한다.

    private final AdminService adminService;

    @Operation(summary = "계정 생성",
            description = "ADMIN_ACCOUNT_CREATE_DELETE 보유자만 호출 가능. "
                    + "track은 ALL 제외 ANALYSIS·VISUALIZATION·ENGINEERING만 허용.")
    @PreAuthorize("hasAuthority('ADMIN_ACCOUNT_CREATE_DELETE')")
    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<AdminIdResponse>> createAccount(
            @RequestBody @Valid AdminCreateRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails
    ) {
        AdminIdResponse response = adminService.createAccount(request, userDetails.getAdmin());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "모든 계정 조회", description = "ADMIN_ACCOUNT_READ 보유자만 호출 가능.")
    @PreAuthorize("hasAuthority('ADMIN_ACCOUNT_READ')")
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AdminAccountResponse>>> getAccounts() {
        List<AdminAccountResponse> response = adminService.getAccounts();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // @PreAuthorize 를 붙이지 않는다 — 주체 자신을 그대로 돌려주는 엔드포인트라 대상 판정이 없다.
    // 권한 0인 계정도 자기 정보는 볼 수 있어야 한다.
    @Operation(summary = "내 계정 정보 조회", description = "인증된 본인의 id, 소속, 이름을 반환.")
    @GetMapping("/accounts/me")
    public ResponseEntity<ApiResponse<AdminMeResponse>> getMe(
            @AuthenticationPrincipal AdminUserDetails userDetails
    ) {
        AdminMeResponse response = adminService.getMe(userDetails.getAdmin());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "id별 계정 조회",
            description = "ADMIN_ACCOUNT_READ 보유 시 전체, ADMIN_ACCOUNT_SELF_READ 만 보유 시 본인 계정만.")
    @PreAuthorize("hasAnyAuthority('ADMIN_ACCOUNT_SELF_READ','ADMIN_ACCOUNT_READ')")
    @GetMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<AdminAccountResponse>> getAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal AdminUserDetails userDetails
    ) {
        AdminAccountResponse response = adminService.getAccount(id, userDetails.getAdmin(), userDetails.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "id별 계정 수정",
            description = "ADMIN_ACCOUNT_WRITE 보유 시 전체, ADMIN_ACCOUNT_SELF_WRITE 만 보유 시 본인 계정만. "
                    + "모든 필드 선택적. role·teamName 변경 시 해당 계정의 RefreshToken 삭제(재로그인 강제). "
                    + "본인 role·teamName 변경 불가.")
    @PreAuthorize("hasAnyAuthority('ADMIN_ACCOUNT_SELF_WRITE','ADMIN_ACCOUNT_WRITE')")
    @PatchMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<AdminIdResponse>> updateAccount(
            @PathVariable Long id,
            @RequestBody AdminUpdateRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails
    ) {
        AdminIdResponse response = adminService.updateAccount(id, request, userDetails.getAdmin(), userDetails.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "id별 계정 삭제",
            description = "ADMIN_ACCOUNT_CREATE_DELETE 보유자만 호출 가능. soft delete. "
                    + "계정 관리 권한(ADMIN_ACCOUNT_CREATE_DELETE)을 가진 마지막 계정은 삭제 불가. "
                    + "삭제 시 RefreshToken도 함께 삭제.")
    @PreAuthorize("hasAuthority('ADMIN_ACCOUNT_CREATE_DELETE')")
    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long id) {
        adminService.deleteAccount(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "비밀번호 변경",
            description = "ADMIN_ACCOUNT_WRITE 보유 시 타인 초기화, ADMIN_ACCOUNT_SELF_WRITE 만 보유 시 본인 계정만. "
                    + "본인 비밀번호 변경 시 currentPassword 필수. 타인 초기화 시 currentPassword 불필요. "
                    + "변경 후 해당 계정의 RefreshToken 삭제(재로그인 강제).")
    @PreAuthorize("hasAnyAuthority('ADMIN_ACCOUNT_SELF_WRITE','ADMIN_ACCOUNT_WRITE')")
    @PatchMapping("/accounts/{id}/password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id,
            @RequestBody @Valid AdminPasswordResetRequest request,
            @AuthenticationPrincipal AdminUserDetails userDetails
    ) {
        adminService.resetPassword(id, request, userDetails.getAdmin(), userDetails.getPermissions());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}