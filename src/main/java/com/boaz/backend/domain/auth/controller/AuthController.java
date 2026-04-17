package com.boaz.backend.domain.auth.controller;

import org.springframework.http.ResponseEntity;

import com.boaz.backend.domain.auth.dto.request.LoginRequest;
import com.boaz.backend.domain.auth.dto.response.LoginResponse;
import com.boaz.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.boaz.backend.domain.auth.service.AuthService;
import com.boaz.backend.global.util.CookieProvider;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private final AuthService authService;
    private final CookieProvider cookieProvider;

    // AUTH-001 로그인
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(

        @Valid @RequestBody LoginRequest request, 
        HttpServletResponse response
    ) {
        LoginResponse result = authService.login(request);

        // 쿠키 설정 
        cookieProvider.addRefreshTokenCookie(response, result.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // AUTH-002 토큰 갱신 
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
        @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        if (refreshToken == null) {
            throw new CustomException(ErrorCode.TOKEN_NOT_FOUND);  // 토큰 미포함 
        }
        TokenRefreshResponse result = authService.refresh(refreshToken);

        return ResponseEntity
            .ok(ApiResponse.ok(result));
    }

    // AUTH-003 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
        @CookieValue(name = "refresh_token", required = false) String refreshToken, 
        HttpServletResponse response
    ) {
        authService.logout(refreshToken);

        // 쿠키는 Controller에서 처리
        cookieProvider.expireRefreshTokenCookie(response);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

}