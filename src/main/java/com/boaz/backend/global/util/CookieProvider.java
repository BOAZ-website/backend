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

    private static final String ADMIN_REFRESH_TOKEN_COOKIE = "admin_refresh_token";
    private static final String USER_REFRESH_TOKEN_COOKIE = "user_refresh_token";

    // Admin Refresh Token 쿠키
    public void addAdminRefreshTokenCookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", buildCookieHeader(ADMIN_REFRESH_TOKEN_COOKIE, token, (int)(refreshTokenExpiration / 1000)));
    }

    public void expireAdminRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookieHeader(ADMIN_REFRESH_TOKEN_COOKIE, "", 0));
    }

    // User Refresh Token 쿠키
    public void addUserRefreshTokenCookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", buildCookieHeader(USER_REFRESH_TOKEN_COOKIE, token, (int)(refreshTokenExpiration / 1000)));
    }

    public void expireUserRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookieHeader(USER_REFRESH_TOKEN_COOKIE, "", 0));
    }

    // OAuth state 쿠키 — SameSite=Lax 고정 (OAuth 콜백은 외부 도메인에서 오므로 Strict 불가)
    public void addOAuth2StateCookie(HttpServletResponse response, String name, String value, int maxAge) {
        response.addHeader("Set-Cookie", buildOAuth2CookieHeader(name, value, maxAge));
    }

    public void deleteOAuth2StateCookie(HttpServletResponse response, String name) {
        response.addHeader("Set-Cookie", buildOAuth2CookieHeader(name, "", 0));
    }

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

    private String buildOAuth2CookieHeader(String name, String value, int maxAge) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value).append("; ");
        sb.append("Max-Age=").append(maxAge).append("; ");
        sb.append("Path=/; ");
        sb.append("HttpOnly; ");
        if (secure) sb.append("Secure; ");
        sb.append("SameSite=Lax");
        if (domain != null && !domain.isBlank()) sb.append("; Domain=").append(domain);
        return sb.toString();
    }
}
