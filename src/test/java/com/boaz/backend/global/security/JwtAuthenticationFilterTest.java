package com.boaz.backend.global.security;

import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtProvider jwtProvider;
    @Mock AdminUserDetailsService adminUserDetailsService;

    JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider, adminUserDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }

    private Claims mockClaims(String subject, String type, String tokenType) {
        Claims claims = mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(subject);
        lenient().when(claims.get("type", String.class)).thenReturn(type);
        lenient().when(claims.get("tokenType", String.class)).thenReturn(tokenType);
        lenient().when(claims.get("username", String.class)).thenReturn(null);
        return claims;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-001 Authorization 헤더 없는 경우
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-001 Authorization 헤더 없음 → 필터 통과, SecurityContext 비어있음")
    void no_auth_header_passes_filter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-002 Bearer 형식이 아닌 경우
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-002 Bearer 형식 아닌 Authorization 헤더 → 필터 통과, SecurityContext 비어있음")
    void non_bearer_passes_filter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-003 유효한 USER Access Token
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-003 유효한 USER Access Token → UserPrincipal + ROLE_USER 세팅 후 통과")
    void valid_user_access_token() throws Exception {
        Claims claims = mockClaims("1", "USER", "ACCESS");
        doNothing().when(jwtProvider).validateToken("valid-user-token");
        when(jwtProvider.parseClaims("valid-user-token")).thenReturn(claims);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBearer("valid-user-token"), response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) auth.getPrincipal()).userId()).isEqualTo(1L);
        assertThat(auth.getAuthorities()).extracting(Object::toString).contains("ROLE_USER");
        assertThat(chain.getRequest()).isNotNull();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TC-004 유효한 ADMIN Access Token
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TC-004 유효한 ADMIN Access Token → AdminUserDetails 세팅 후 통과")
    void valid_admin_access_token() throws Exception {
        Claims claims = mockClaims("10", "ADMIN", "ACCESS");
        when(claims.get("username", String.class)).thenReturn("admin01");

        doNothing().when(jwtProvider).validateToken("valid-admin-token");
        when(jwtProvider.parseClaims("valid-admin-token")).thenReturn(claims);

        UserDetails adminDetails = mock(UserDetails.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_SUPER"))).when(adminDetails).getAuthorities();
        when(adminUserDetailsService.loadUserByUsername("admin01")).thenReturn(adminDetails);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBearer("valid-admin-token"), response, chain);

        verify(adminUserDetailsService).loadUserByUsername("admin01");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(UserDetails.class);
        assertThat(auth.getAuthorities()).extracting(Object::toString).contains("ROLE_SUPER");
        assertThat(chain.getRequest()).isNotNull();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 예외 케이스 그룹
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("예외 케이스")
    class ExceptionCases {

        @Test
        @DisplayName("TC-005 만료된 토큰 → 401 EXPIRED_TOKEN, chain 중단")
        void expired_token() throws Exception {
            doThrow(new CustomException(ErrorCode.EXPIRED_TOKEN))
                    .when(jwtProvider).validateToken("expired-token");

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(requestWithBearer("expired-token"), response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("EXPIRED_TOKEN");
            assertThat(chain.getRequest()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("TC-006 위조된 토큰 → 401 INVALID_TOKEN, chain 중단")
        void forged_token() throws Exception {
            doThrow(new CustomException(ErrorCode.INVALID_TOKEN))
                    .when(jwtProvider).validateToken("forged-token");

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(requestWithBearer("forged-token"), response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
            assertThat(chain.getRequest()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("TC-007 tokenType=REFRESH → 401 INVALID_TOKEN, chain 중단")
        void refresh_token_used_as_access() throws Exception {
            Claims claims = mockClaims("1", "USER", "REFRESH");
            doNothing().when(jwtProvider).validateToken("refresh-token");
            when(jwtProvider.parseClaims("refresh-token")).thenReturn(claims);

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(requestWithBearer("refresh-token"), response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("TC-008 type=GUEST (알 수 없는 type) → 401 INVALID_TOKEN, chain 중단")
        void unknown_type() throws Exception {
            Claims claims = mockClaims("1", "GUEST", "ACCESS");
            doNothing().when(jwtProvider).validateToken("unknown-type-token");
            when(jwtProvider.parseClaims("unknown-type-token")).thenReturn(claims);

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(requestWithBearer("unknown-type-token"), response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("TC-009 예기치 못한 예외 → 500 INTERNAL_SERVER_ERROR, chain 중단")
        void unexpected_exception() throws Exception {
            doNothing().when(jwtProvider).validateToken("some-token");
            when(jwtProvider.parseClaims("some-token")).thenThrow(new RuntimeException("unexpected"));

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(requestWithBearer("some-token"), response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentAsString()).contains("INTERNAL_SERVER_ERROR");
            assertThat(chain.getRequest()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
