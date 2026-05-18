package com.boaz.backend.domain.auth.controller;

import com.boaz.backend.domain.auth.dto.response.UserTokenRefreshResponse;
import com.boaz.backend.domain.auth.service.UserAuthService;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.security.UserPrincipal;
import com.boaz.backend.global.util.CookieProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Auth", description = "User 인증 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/user")
public class UserAuthController {

    private final UserAuthService userAuthService;
    private final CookieProvider cookieProvider;

    // AUTH-005 User 토큰 재발급
    @Operation(summary = "User 토큰 재발급", description = "user_refresh_token 쿠키로 Access Token 재발급")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<UserTokenRefreshResponse>> refresh(
            @CookieValue(name = "user_refresh_token", required = false) String refreshToken
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userAuthService.refreshToken(refreshToken)));
    }

    // AUTH-006 User 로그아웃
    @Operation(summary = "User 로그아웃", description = "Refresh Token 무효화 및 쿠키 삭제")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletResponse response
    ) {
        userAuthService.logout(principal.userId());
        cookieProvider.expireUserRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
