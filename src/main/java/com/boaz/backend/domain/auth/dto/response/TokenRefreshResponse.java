package com.boaz.backend.domain.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 토큰 갱신 응답 (요청은 Cookie로)
@Getter
@AllArgsConstructor
public class TokenRefreshResponse {
    
    private String accessToken;
}
