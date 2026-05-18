package com.boaz.backend.domain.auth.controller;

import com.boaz.backend.domain.auth.dto.request.LoginRequest;
import com.boaz.backend.domain.auth.dto.response.LoginResponse;
import com.boaz.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.boaz.backend.domain.auth.service.AdminAuthService;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.security.AdminUserDetails;
import com.boaz.backend.global.util.CookieProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Auth", description = "어드민 인증 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final CookieProvider cookieProvider;

    @Operation(summary = "어드민 로그인", description = "아이디/비밀번호로 로그인. Access Token 반환 및 Refresh Token 쿠키 설정")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginResponse result = adminAuthService.login(request);
        cookieProvider.addAdminRefreshTokenCookie(response, result.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "어드민 토큰 갱신", description = "admin_refresh_token 쿠키로 Access Token 재발급")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @CookieValue(name = "admin_refresh_token", required = false) String refreshToken
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminAuthService.refresh(refreshToken)));
    }

    @Operation(summary = "어드민 로그아웃", description = "Refresh Token 무효화 및 쿠키 삭제")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AdminUserDetails userDetails,
            HttpServletResponse response
    ) {
        adminAuthService.logout(userDetails.getAdmin().getId());
        cookieProvider.expireAdminRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
