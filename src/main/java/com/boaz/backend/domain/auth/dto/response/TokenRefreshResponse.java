package com.boaz.backend.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 토큰 갱신 응답 (요청은 Cookie로)
@Getter
@AllArgsConstructor
public class TokenRefreshResponse {
    
    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9..")
    private String accessToken;
}
