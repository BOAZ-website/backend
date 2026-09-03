package com.boaz.backend.domain.auth.service;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.auth.dto.request.LoginRequest;
import com.boaz.backend.domain.auth.dto.response.LoginResponse;
import com.boaz.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.boaz.backend.domain.auth.entity.RefreshToken;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.global.common.enums.AccountType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.JwtProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks AuthService authService;

    @Mock AdminRepository adminRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtProvider jwtProvider;
    @Mock PasswordEncoder passwordEncoder;

    private RefreshToken buildRefreshToken(String token) {
        return RefreshToken.builder()
                .accountType(AccountType.USER)
                .accountId(1L)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    private RefreshToken buildAdminRefreshToken(Long adminId, String token) {
        return RefreshToken.builder()
                .accountType(AccountType.ADMIN)
                .accountId(adminId)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    private Admin buildAdmin(Long id, String username, String encodedPassword) {
        Admin admin = Admin.builder()
                .username(username).password(encodedPassword).role(Admin.Role.TEAM)
                .name("name" + id).track(Track.ANALYSIS).term(25).teamName(Admin.TeamName.기획팀).createdBy(null)
                .build();
        ReflectionTestUtils.setField(admin, "id", id);
        return admin;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "username", username);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

    private Claims stubClaims(String subject) {
        Claims claims = mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(subject);
        return claims;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-001 관리자 로그인 - adminLogin
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-001 adminLogin")
    class AdminLogin {

        @Test
        @DisplayName("TC-001 정상 로그인 (기존 RefreshToken 없음) → 토큰 발급 + 신규 저장")
        void success_new_refresh_token() {
            Admin admin = buildAdmin(1L, "boaz_team", "ENCODED");
            when(adminRepository.findByUsernameAndDeletedAtIsNull("boaz_team"))
                    .thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("Boaz1234!", "ENCODED")).thenReturn(true);
            when(jwtProvider.generateAdminAccessToken(1L, "boaz_team", "TEAM")).thenReturn("access-token");
            when(jwtProvider.generateAdminRefreshToken(1L)).thenReturn("refresh-token");
            when(jwtProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(7));
            when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.ADMIN, 1L))
                    .thenReturn(Optional.empty());

            LoginResponse response = authService.adminLogin(loginRequest("boaz_team", "Boaz1234!"));

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("TC-002 정상 로그인 (기존 RefreshToken 존재) → 토큰 발급 + 기존 로우 갱신")
        void success_existing_refresh_token_updated() {
            Admin admin = buildAdmin(1L, "boaz_team", "ENCODED");
            RefreshToken existing = buildAdminRefreshToken(1L, "old-refresh-token");
            when(adminRepository.findByUsernameAndDeletedAtIsNull("boaz_team"))
                    .thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("Boaz1234!", "ENCODED")).thenReturn(true);
            when(jwtProvider.generateAdminAccessToken(1L, "boaz_team", "TEAM")).thenReturn("access-token");
            when(jwtProvider.generateAdminRefreshToken(1L)).thenReturn("new-refresh-token");
            when(jwtProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(7));
            when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.ADMIN, 1L))
                    .thenReturn(Optional.of(existing));

            authService.adminLogin(loginRequest("boaz_team", "Boaz1234!"));

            assertThat(existing.getToken()).isEqualTo("new-refresh-token");
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("TC-003 존재하지 않는 username → INVALID_CREDENTIALS")
        void username_not_found() {
            when(adminRepository.findByUsernameAndDeletedAtIsNull("nobody"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.adminLogin(loginRequest("nobody", "Boaz1234!")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            verify(jwtProvider, never()).generateAdminAccessToken(any(), any(), any());
        }

        @Test
        @DisplayName("TC-004 비밀번호 불일치 → INVALID_CREDENTIALS")
        void password_mismatch() {
            Admin admin = buildAdmin(1L, "boaz_team", "ENCODED");
            when(adminRepository.findByUsernameAndDeletedAtIsNull("boaz_team"))
                    .thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("wrong-password", "ENCODED")).thenReturn(false);

            assertThatThrownBy(() -> authService.adminLogin(loginRequest("boaz_team", "wrong-password")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            verify(jwtProvider, never()).generateAdminAccessToken(any(), any(), any());
        }

        @Test
        @DisplayName("TC-005 soft delete 된 계정 → INVALID_CREDENTIALS (조회 자체가 안 됨)")
        void deleted_account() {
            when(adminRepository.findByUsernameAndDeletedAtIsNull("boaz_team"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.adminLogin(loginRequest("boaz_team", "Boaz1234!")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-002 관리자 토큰 갱신 - adminRefresh
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-002 adminRefresh")
    class AdminRefresh {

        @Test
        @DisplayName("TC-001 유효한 Refresh Token 쿠키 → 새 Access Token 반환")
        void valid_refresh_token() {
            Admin admin = buildAdmin(1L, "boaz_team", "ENCODED");
            RefreshToken saved = buildAdminRefreshToken(1L, "valid-refresh-token");
            doNothing().when(jwtProvider).validateToken("valid-refresh-token");
            when(refreshTokenRepository.findByAccountTypeAndToken(AccountType.ADMIN, "valid-refresh-token"))
                    .thenReturn(Optional.of(saved));
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(jwtProvider.generateAdminAccessToken(1L, "boaz_team", "TEAM")).thenReturn("new-access-token");

            TokenRefreshResponse response = authService.adminRefresh("valid-refresh-token");

            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        }

        @Test
        @DisplayName("TC-002 만료된 Refresh Token → EXPIRED_TOKEN")
        void expired_token() {
            doThrow(new CustomException(ErrorCode.EXPIRED_TOKEN))
                    .when(jwtProvider).validateToken("expired-refresh-token");

            assertThatThrownBy(() -> authService.adminRefresh("expired-refresh-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.EXPIRED_TOKEN);

            verify(refreshTokenRepository, never()).findByAccountTypeAndToken(any(), any());
        }

        @Test
        @DisplayName("TC-003 위조된 Refresh Token → INVALID_TOKEN")
        void forged_token() {
            doThrow(new CustomException(ErrorCode.INVALID_TOKEN))
                    .when(jwtProvider).validateToken("forged-refresh-token");

            assertThatThrownBy(() -> authService.adminRefresh("forged-refresh-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("TC-004 쿠키가 null → TOKEN_NOT_FOUND")
        void cookie_null() {
            assertThatThrownBy(() -> authService.adminRefresh(null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_NOT_FOUND);

            verify(jwtProvider, never()).validateToken(any());
        }

        @Test
        @DisplayName("TC-005 쿠키가 공백 → TOKEN_NOT_FOUND")
        void cookie_blank() {
            assertThatThrownBy(() -> authService.adminRefresh("   "))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_NOT_FOUND);

            verify(jwtProvider, never()).validateToken(any());
        }

        @Test
        @DisplayName("TC-006 DB에 Refresh Token 없음 → INVALID_TOKEN")
        void no_db_token() {
            doNothing().when(jwtProvider).validateToken("valid-refresh-token");
            when(refreshTokenRepository.findByAccountTypeAndToken(AccountType.ADMIN, "valid-refresh-token"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.adminRefresh("valid-refresh-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);

            verify(adminRepository, never()).findById(any());
        }

        @Test
        @DisplayName("TC-007 DB 토큰은 있으나 계정이 존재하지 않음(soft delete 포함) → ADMIN_NOT_FOUND")
        void admin_not_found_or_deleted() {
            RefreshToken saved = buildAdminRefreshToken(1L, "valid-refresh-token");
            doNothing().when(jwtProvider).validateToken("valid-refresh-token");
            when(refreshTokenRepository.findByAccountTypeAndToken(AccountType.ADMIN, "valid-refresh-token"))
                    .thenReturn(Optional.of(saved));
            when(adminRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.adminRefresh("valid-refresh-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_NOT_FOUND);

            verify(jwtProvider, never()).generateAdminAccessToken(any(), any(), any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-003 관리자 로그아웃 - adminLogout
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-003 adminLogout")
    class AdminLogout {

        @Test
        @DisplayName("TC-001 로그아웃 → DB Refresh Token 삭제")
        void logout_deletes_refresh_token() {
            authService.adminLogout(1L);

            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 1L);
        }

        @Test
        @DisplayName("TC-002 이미 로그아웃 상태 (DB에 토큰 없음) → 예외 없이 정상 처리")
        void logout_already_logged_out() {
            doNothing().when(refreshTokenRepository)
                    .deleteByAccountTypeAndAccountId(AccountType.ADMIN, 1L);

            authService.adminLogout(1L);

            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 1L);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-005 사용자 토큰 갱신 - userRefresh
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-005 userRefresh")
    class UserRefresh {

        @Test
        @DisplayName("TC-001 유효한 Refresh Token 쿠키 → 새 Access Token 반환")
        void valid_refresh_token() {
            RefreshToken saved = buildRefreshToken("valid-refresh-token");
            Claims claims = stubClaims("1");
            doNothing().when(jwtProvider).validateToken("valid-refresh-token");
            when(jwtProvider.parseClaims("valid-refresh-token")).thenReturn(claims);
            when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, 1L))
                    .thenReturn(Optional.of(saved));
            when(jwtProvider.generateUserAccessToken(1L)).thenReturn("new-access-token");

            TokenRefreshResponse response = authService.userRefresh("valid-refresh-token");

            verify(jwtProvider).validateToken("valid-refresh-token");
            verify(jwtProvider).parseClaims("valid-refresh-token");
            verify(refreshTokenRepository).findByAccountTypeAndAccountId(AccountType.USER, 1L);
            verify(jwtProvider).generateUserAccessToken(1L);
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        }

        @Test
        @DisplayName("TC-002 쿠키가 null → TOKEN_NOT_FOUND")
        void cookie_null() {
            assertThatThrownBy(() -> authService.userRefresh(null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_NOT_FOUND);

            verify(jwtProvider, never()).validateToken(any());
        }

        @Test
        @DisplayName("TC-003 쿠키가 공백 → TOKEN_NOT_FOUND")
        void cookie_blank() {
            assertThatThrownBy(() -> authService.userRefresh("   "))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_NOT_FOUND);

            verify(jwtProvider, never()).validateToken(any());
        }

        @Test
        @DisplayName("TC-004 만료된 Refresh Token → EXPIRED_TOKEN")
        void expired_token() {
            doThrow(new CustomException(ErrorCode.EXPIRED_TOKEN))
                    .when(jwtProvider).validateToken("expired-refresh-token");

            assertThatThrownBy(() -> authService.userRefresh("expired-refresh-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.EXPIRED_TOKEN);

            verify(jwtProvider).validateToken("expired-refresh-token");
            verify(refreshTokenRepository, never()).findByAccountTypeAndAccountId(any(), any());
        }

        @Test
        @DisplayName("TC-005 위조된 Refresh Token → INVALID_TOKEN")
        void forged_token() {
            doThrow(new CustomException(ErrorCode.INVALID_TOKEN))
                    .when(jwtProvider).validateToken("forged-refresh-token");

            assertThatThrownBy(() -> authService.userRefresh("forged-refresh-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);

            verify(refreshTokenRepository, never()).findByAccountTypeAndAccountId(any(), any());
        }

        @Test
        @DisplayName("TC-006 DB에 Refresh Token 없음 → INVALID_TOKEN")
        void no_db_token() {
            Claims claims = stubClaims("1");
            doNothing().when(jwtProvider).validateToken("valid-refresh-token");
            when(jwtProvider.parseClaims("valid-refresh-token")).thenReturn(claims);
            when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.userRefresh("valid-refresh-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);

            verify(jwtProvider, never()).generateUserAccessToken(any());
        }

        @Test
        @DisplayName("TC-007 DB 토큰과 쿠키 토큰 불일치 → INVALID_TOKEN")
        void token_mismatch() {
            RefreshToken saved = buildRefreshToken("new-refresh-token");
            Claims claims = stubClaims("1");
            doNothing().when(jwtProvider).validateToken("old-refresh-token");
            when(jwtProvider.parseClaims("old-refresh-token")).thenReturn(claims);
            when(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, 1L))
                    .thenReturn(Optional.of(saved));

            assertThatThrownBy(() -> authService.userRefresh("old-refresh-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);

            verify(jwtProvider, never()).generateUserAccessToken(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AUTH-006 사용자 로그아웃 - userLogout
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("AUTH-006 userLogout")
    class UserLogout {

        @Test
        @DisplayName("TC-001 로그아웃 → DB Refresh Token 삭제")
        void logout_deletes_refresh_token() {
            authService.userLogout(1L);

            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.USER, 1L);
        }

        @Test
        @DisplayName("TC-002 이미 로그아웃 상태 (DB에 토큰 없음) → 예외 없이 정상 처리")
        void logout_already_logged_out() {
            doNothing().when(refreshTokenRepository)
                    .deleteByAccountTypeAndAccountId(AccountType.USER, 1L);

            authService.userLogout(1L);

            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.USER, 1L);
        }
    }
}
