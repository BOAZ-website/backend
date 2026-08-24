package com.boaz.backend.domain.auth.controller;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.auth.dto.response.LoginResponse;
import com.boaz.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.boaz.backend.domain.auth.service.AuthService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.AdminUserDetails;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(
        value = AdminAuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class AdminAuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean CookieProvider cookieProvider;

    private Admin admin(Long id) {
        Admin a = Admin.builder()
                .username("boaz_team").password("ENCODED").role(Admin.Role.TEAM)
                .name("name" + id).track(Track.ANALYSIS).term(25).teamName(Admin.TeamName.기획팀).createdBy(null)
                .build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        AdminUserDetails principal = new AdminUserDetails(admin(1L));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-001 POST /api/v1/auth/admin/login
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-001 POST /api/v1/auth/admin/login")
    class Login {

        @Test
        @DisplayName("TC-001 정상 로그인 → 200 + Access Token 반환 + Refresh Token 쿠키 설정")
        void login_success() throws Exception {
            when(authService.adminLogin(any())).thenReturn(new LoginResponse("access-token", "refresh-token"));
            doNothing().when(cookieProvider).addAdminRefreshTokenCookie(any(), any());

            mockMvc.perform(post("/api/v1/auth/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"boaz_team\",\"password\":\"Boaz1234!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.access_token").value("access-token"));

            verify(cookieProvider).addAdminRefreshTokenCookie(any(), eq("refresh-token"));
        }

        @Test
        @DisplayName("TC-002 아이디/비밀번호 불일치 → 401 INVALID_CREDENTIALS")
        void login_invalid_credentials() throws Exception {
            when(authService.adminLogin(any())).thenThrow(new CustomException(ErrorCode.INVALID_CREDENTIALS));

            mockMvc.perform(post("/api/v1/auth/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"boaz_team\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"));

            verify(cookieProvider, never()).addAdminRefreshTokenCookie(any(), any());
        }

        @Test
        @DisplayName("TC-003 username 누락 → 400 (@Valid)")
        void login_missing_username() throws Exception {
            mockMvc.perform(post("/api/v1/auth/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"Boaz1234!\"}"))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).adminLogin(any());
        }

        @Test
        @DisplayName("TC-004 password 누락 → 400 (@Valid)")
        void login_missing_password() throws Exception {
            mockMvc.perform(post("/api/v1/auth/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"boaz_team\"}"))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).adminLogin(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-002 POST /api/v1/auth/admin/refresh
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-002 POST /api/v1/auth/admin/refresh")
    class Refresh {

        @Test
        @DisplayName("TC-001 유효한 쿠키 → 200 + 새 Access Token 반환")
        void refresh_success() throws Exception {
            when(authService.adminRefresh("valid-refresh-token"))
                    .thenReturn(new TokenRefreshResponse("new-access-token"));

            mockMvc.perform(post("/api/v1/auth/admin/refresh")
                            .cookie(new Cookie("admin_refresh_token", "valid-refresh-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.access_token").value("new-access-token"));
        }

        @Test
        @DisplayName("TC-002 쿠키 없이 요청 → 401 TOKEN_NOT_FOUND (서비스 레이어에서 거부)")
        void refresh_no_cookie() throws Exception {
            when(authService.adminRefresh(null))
                    .thenThrow(new CustomException(ErrorCode.TOKEN_NOT_FOUND));

            mockMvc.perform(post("/api/v1/auth/admin/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }

        @Test
        @DisplayName("TC-003 만료된 Refresh Token → 401 EXPIRED_TOKEN")
        void refresh_expired_token() throws Exception {
            when(authService.adminRefresh("expired-refresh-token"))
                    .thenThrow(new CustomException(ErrorCode.EXPIRED_TOKEN));

            mockMvc.perform(post("/api/v1/auth/admin/refresh")
                            .cookie(new Cookie("admin_refresh_token", "expired-refresh-token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("EXPIRED_TOKEN"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-003 POST /api/v1/auth/admin/logout
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-003 POST /api/v1/auth/admin/logout")
    class Logout {

        @Test
        @DisplayName("TC-001 유효한 Access Token → 200 OK + 쿠키 만료 처리")
        void logout_success() throws Exception {
            doNothing().when(authService).adminLogout(1L);
            doNothing().when(cookieProvider).expireAdminRefreshTokenCookie(any());

            mockMvc.perform(post("/api/v1/auth/admin/logout")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(authService).adminLogout(1L);
            verify(cookieProvider).expireAdminRefreshTokenCookie(any());
        }

        @Test
        @DisplayName("TC-002 인증 없이 접근 → 401")
        void logout_unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/auth/admin/logout"))
                    .andExpect(status().isUnauthorized());

            verify(authService, never()).adminLogout(anyLong());
        }
    }
}
