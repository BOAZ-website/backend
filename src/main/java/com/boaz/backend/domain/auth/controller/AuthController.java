package com.boaz.backend.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import com.boaz.backend.global.security.AdminUserDetails;

import com.boaz.backend.domain.auth.dto.request.LoginRequest;
import com.boaz.backend.domain.auth.dto.response.LoginResponse;
import com.boaz.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.boaz.backend.domain.auth.service.AuthService;
import com.boaz.backend.global.util.CookieProvider;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.boaz.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private final AuthService authService;
    private final CookieProvider cookieProvider;

    // AUTH-001 로그인
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인. 성공 시 Access Token 반환 및 Refresh Token 쿠키 설정")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request, 
        HttpServletResponse response
    ) {
        LoginResponse result = authService.login(request);

        // 쿠키 설정 (RefreshToken)
        cookieProvider.addRefreshTokenCookie(response, result.getRefreshToken());

        // Body로 (AccessToken)
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // AUTH-002 토큰 갱신 
    @Operation(summary = "토큰 갱신", description = "Cookie의 Refresh Token으로 Access Token 재발급")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
        @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        TokenRefreshResponse result = authService.refresh(refreshToken);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // AUTH-003 로그아웃
    @Operation(summary = "로그아웃", description = "Refresh Token 무효화 및 Cookie 삭제")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
        HttpServletResponse response
    ) {
        // SecurityContext에서 adminId 추출
        AdminUserDetails adminUserDetails = (AdminUserDetails) SecurityContextHolder
            .getContext().getAuthentication().getPrincipal();
        Long adminId = adminUserDetails.getAdmin().getId();
        // DB에서 토큰 삭제 (Service에서 처리)
        authService.logout(adminId);

        // 브라우저에 저장된 쿠키 삭제 (Controller에서 처리)
        cookieProvider.expireRefreshTokenCookie(response);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

}