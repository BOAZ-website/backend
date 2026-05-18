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

    private static final String ADMIN_REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String USER_REFRESH_TOKEN_COOKIE = "user_refresh_token";

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        String cookie = buildCookieHeader(ADMIN_REFRESH_TOKEN_COOKIE, refreshToken, (int)(refreshTokenExpiration / 1000));
        response.addHeader("Set-Cookie", cookie);
    }

    public void expireRefreshTokenCookie(HttpServletResponse response) {
        String cookie = buildCookieHeader(ADMIN_REFRESH_TOKEN_COOKIE, "", 0);
        response.addHeader("Set-Cookie", cookie);
    }

    public void addUserRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        String cookie = buildCookieHeader(USER_REFRESH_TOKEN_COOKIE, refreshToken, (int)(refreshTokenExpiration / 1000));
        response.addHeader("Set-Cookie", cookie);
    }

    public void expireUserRefreshTokenCookie(HttpServletResponse response) {
        String cookie = buildCookieHeader(USER_REFRESH_TOKEN_COOKIE, "", 0);
        response.addHeader("Set-Cookie", cookie);
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
}
