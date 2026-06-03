package com.boaz.backend.global.security;

import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHktbXVzdC1iZS1sb25nZW5vdWdo";
    private static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LWtleS1mb3ItdGVzdGluZy1vbmx5LW11c3QtYmUtbG9uZ2Vub3VnaA==";
    private static final long ACCESS_EXP = 900000L;
    private static final long REFRESH_EXP = 604800000L;

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, ACCESS_EXP, REFRESH_EXP);

    // ──────────────────────────────────────────────────────────────────────────
    // [그룹 A] User 토큰 생성
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("[그룹 A] User 토큰 생성")
    class UserTokenGeneration {

        @Test
        @DisplayName("TC-001 generateUserAccessToken → 클레임 검증")
        void user_access_token_claims() {
            String token = jwtProvider.generateUserAccessToken(1L);
            Claims claims = jwtProvider.parseClaims(token);

            assertThat(claims.getSubject()).isEqualTo("1");
            assertThat(claims.get("type", String.class)).isEqualTo("USER");
            assertThat(claims.get("tokenType", String.class)).isEqualTo("ACCESS");
            assertThat(claims.getExpiration().after(new Date())).isTrue();
        }

        @Test
        @DisplayName("TC-002 generateUserRefreshToken → 클레임 검증")
        void user_refresh_token_claims() {
            String token = jwtProvider.generateUserRefreshToken(1L);
            Claims claims = jwtProvider.parseClaims(token);

            assertThat(claims.getSubject()).isEqualTo("1");
            assertThat(claims.get("type", String.class)).isEqualTo("USER");
            assertThat(claims.get("tokenType", String.class)).isEqualTo("REFRESH");
            assertThat(claims.getExpiration().after(new Date())).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // [그룹 B] Admin 토큰 생성
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("[그룹 B] Admin 토큰 생성")
    class AdminTokenGeneration {

        @Test
        @DisplayName("TC-003 generateAdminAccessToken → username/role 클레임 포함")
        void admin_access_token_claims() {
            String token = jwtProvider.generateAdminAccessToken(10L, "admin01", "ROLE_SUPER");
            Claims claims = jwtProvider.parseClaims(token);

            assertThat(claims.getSubject()).isEqualTo("10");
            assertThat(claims.get("type", String.class)).isEqualTo("ADMIN");
            assertThat(claims.get("tokenType", String.class)).isEqualTo("ACCESS");
            assertThat(claims.get("username", String.class)).isEqualTo("admin01");
            assertThat(claims.get("role", String.class)).isEqualTo("ROLE_SUPER");
            assertThat(claims.getExpiration().after(new Date())).isTrue();
        }

        @Test
        @DisplayName("TC-004 generateAdminRefreshToken → username 클레임 없음")
        void admin_refresh_token_no_username() {
            String token = jwtProvider.generateAdminRefreshToken(10L);
            Claims claims = jwtProvider.parseClaims(token);

            assertThat(claims.getSubject()).isEqualTo("10");
            assertThat(claims.get("type", String.class)).isEqualTo("ADMIN");
            assertThat(claims.get("tokenType", String.class)).isEqualTo("REFRESH");
            assertThat(claims.get("username", String.class)).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // [그룹 C] validateToken
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("[그룹 C] validateToken")
    class ValidateToken {

        @Test
        @DisplayName("TC-005 유효한 토큰 → 예외 없음")
        void valid_token_no_exception() {
            String token = jwtProvider.generateUserAccessToken(1L);
            jwtProvider.validateToken(token); // should not throw
        }

        @Test
        @DisplayName("TC-006 만료된 토큰 → EXPIRED_TOKEN")
        void expired_token() throws InterruptedException {
            JwtProvider shortExpiry = new JwtProvider(SECRET, 1L, REFRESH_EXP);
            String token = shortExpiry.generateUserAccessToken(1L);
            Thread.sleep(10);

            assertThatThrownBy(() -> jwtProvider.validateToken(token))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.EXPIRED_TOKEN);
        }

        @Test
        @DisplayName("TC-007 다른 키로 서명된 토큰 → INVALID_TOKEN")
        void forged_token() {
            JwtProvider otherProvider = new JwtProvider(OTHER_SECRET, ACCESS_EXP, REFRESH_EXP);
            String forgedToken = otherProvider.generateUserAccessToken(1L);

            assertThatThrownBy(() -> jwtProvider.validateToken(forgedToken))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("TC-008 형식 오류 토큰 → INVALID_TOKEN")
        void malformed_token() {
            assertThatThrownBy(() -> jwtProvider.validateToken("this.is.not.a.jwt"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // [그룹 D] parseClaims
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("[그룹 D] parseClaims")
    class ParseClaims {

        @Test
        @DisplayName("TC-009 유효한 USER Access Token → Claims 정상 반환")
        void parse_user_access_token() {
            String token = jwtProvider.generateUserAccessToken(1L);
            Claims claims = jwtProvider.parseClaims(token);

            assertThat(claims.getSubject()).isEqualTo("1");
            assertThat(claims.get("type", String.class)).isEqualTo("USER");
            assertThat(claims.get("tokenType", String.class)).isEqualTo("ACCESS");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // [그룹 E] getRefreshTokenExpiry
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("[그룹 E] getRefreshTokenExpiry")
    class RefreshTokenExpiry {

        @Test
        @DisplayName("TC-010 getRefreshTokenExpiry → 현재 시각 + refreshTokenExpiration 초")
        void refresh_token_expiry() {
            LocalDateTime before = LocalDateTime.now();
            LocalDateTime expiry = jwtProvider.getRefreshTokenExpiry();

            assertThat(expiry.isAfter(before.plusDays(6))).isTrue();
            assertThat(expiry.isBefore(before.plusDays(8))).isTrue();
        }
    }
}
