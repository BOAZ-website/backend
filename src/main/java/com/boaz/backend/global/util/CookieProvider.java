package com.boaz.backend.global.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class CookieProvider {

    @Value("${cookie.same-site}")
    private String sameSite;

    @Value("${cookie.secure}")
    private boolean secure;

    @Value("${cookie.domain}")
    private String domain;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    // Refresh Token 쿠키 헤더 문자열 생성 후 응답 헤더에 추가 
    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        String cookie = buildCookieHeader(REFRESH_TOKEN_COOKIE_NAME, refreshToken, (int)(refreshTokenExpiration / 1000));
        response.addHeader("Set-Cookie", cookie);
    }

    // Refresh Token 쿠키 만료 (Max-Age=0)
    public void expireRefreshTokenCookie(HttpServletResponse response) {
        String cookie = buildCookieHeader(REFRESH_TOKEN_COOKIE_NAME, "", 0);
        response.addHeader("Set-Cookie", cookie);
    }

    // Set-Cookie 헤더 문자열 생성 
    private String buildCookieHeader(String name, String value, int maxAge) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value).append("; ");
        sb.append("Max-Age=").append(maxAge).append("; ");
        sb.append("Path=/; ");
        sb.append("HttpOnly; "); 
        if (secure) sb.append("Secure; "); 
        sb.append("SameSite=").append(sameSite);
        if (domain != null && !domain.isBlank()) sb.append("; Domain=").append(domain);
        return sb.toString();
    }  
}