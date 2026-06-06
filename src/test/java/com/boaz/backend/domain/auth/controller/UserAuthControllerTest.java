package com.boaz.backend.domain.auth.controller;

import com.boaz.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.boaz.backend.domain.auth.service.AuthService;
import com.boaz.backend.global.security.UserPrincipal;
import com.boaz.backend.global.util.CookieProvider;
import com.boaz.backend.support.TestSecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(
        value = UserAuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class UserAuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean CookieProvider cookieProvider;

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-006 POST /api/v1/auth/user/logout
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-006 POST /api/v1/auth/user/logout")
    class Logout {

        @Test
        @DisplayName("TC-003 유효한 Access Token → 200 OK + 쿠키 만료 처리")
        void logout_success() throws Exception {
            doNothing().when(authService).userLogout(1L);
            doNothing().when(cookieProvider).expireUserRefreshTokenCookie(any());

            mockMvc.perform(post("/api/v1/auth/user/logout")
                            .with(authentication(userAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(authService).userLogout(1L);
            verify(cookieProvider).expireUserRefreshTokenCookie(any());
        }

        @Test
        @DisplayName("TC-004 인증 없이 접근 → 401")
        void logout_unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/auth/user/logout"))
                    .andExpect(status().isUnauthorized());

            verify(authService, never()).userLogout(anyLong());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-005 POST /api/v1/auth/user/refresh
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-005 POST /api/v1/auth/user/refresh")
    class Refresh {

        @Test
        @DisplayName("유효한 쿠키 → 200 OK + 새 Access Token 반환")
        void refresh_success() throws Exception {
            when(authService.userRefresh("valid-refresh-token"))
                    .thenReturn(new TokenRefreshResponse("new-access-token"));

            mockMvc.perform(post("/api/v1/auth/user/refresh")
                            .cookie(new Cookie("user_refresh_token", "valid-refresh-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.access_token").value("new-access-token"));
        }
    }
}
