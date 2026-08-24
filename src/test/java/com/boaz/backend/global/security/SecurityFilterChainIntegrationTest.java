package com.boaz.backend.global.security;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.support.TestcontainersBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TestSecurityConfig(직접 Authentication 주입)을 우회하지 않고, 실제 SecurityConfig 필터체인
 * (JwtAuthenticationFilter → AdminUserDetailsService → 인가 → CustomAccessDeniedHandler/
 * CustomAuthenticationEntryPoint)이 실제로 연결되어 동작하는지 검증하는 end-to-end 테스트.
 * 기존 통합테스트(AdminIntegrationTest 등)는 전부 서비스 빈을 직접 호출해 HTTP/필터체인을 태우지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class SecurityFilterChainIntegrationTest extends TestcontainersBase {

    @Autowired MockMvc mockMvc;
    @Autowired AdminRepository adminRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtProvider jwtProvider;

    private void saveAdmin(String username, String rawPassword, Admin.Role role) {
        adminRepository.save(Admin.builder()
                .username(username).password(passwordEncoder.encode(rawPassword)).role(role)
                .name("김보아즈").track(Track.ANALYSIS).term(25).teamName(Admin.TeamName.기획팀).createdBy(null)
                .build());
    }

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.access_token");
    }

    @Nested
    @DisplayName("실제 필터체인 - 관리자 보호 엔드포인트 (GET /api/v1/admin/accounts/me)")
    class ProtectedAdminEndpoint {

        @Test
        @DisplayName("TC-001 실제 로그인으로 발급받은 Access Token → 필터체인 통과 → 200")
        void real_token_passes_filter_chain() throws Exception {
            saveAdmin("filterchain_admin", "Boaz1234!", Admin.Role.TEAM);
            String accessToken = loginAndGetAccessToken("filterchain_admin", "Boaz1234!");

            mockMvc.perform(get("/api/v1/admin/accounts/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("김보아즈"));
        }

        @Test
        @DisplayName("TC-002 토큰 없이 호출 → 401 TOKEN_NOT_FOUND (CustomAuthenticationEntryPoint)")
        void no_token_rejected_by_entry_point() throws Exception {
            mockMvc.perform(get("/api/v1/admin/accounts/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }

        @Test
        @DisplayName("TC-003 USER 롤 토큰으로 관리자 엔드포인트 호출 → 403 ACCESS_DENIED (CustomAccessDeniedHandler)")
        void user_token_rejected_by_access_denied_handler() throws Exception {
            String userAccessToken = jwtProvider.generateUserAccessToken(999L);

            mockMvc.perform(get("/api/v1/admin/accounts/me")
                            .header("Authorization", "Bearer " + userAccessToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("TC-004 위조/파싱 불가 토큰 → 401 INVALID_TOKEN (JwtAuthenticationFilter 자체 응답)")
        void forged_token_rejected_by_filter() throws Exception {
            mockMvc.perform(get("/api/v1/admin/accounts/me")
                            .header("Authorization", "Bearer this-is-not-a-valid-jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("INVALID_TOKEN"));
        }
    }
}
