package com.boaz.backend.global.security;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.logging.RequestLoggingFilter;
import com.boaz.backend.support.TestcontainersBase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * 요청 로그 필터는 Security보다 앞에 있으므로, 보안 필터가 끝낸 401·403도 남아야 한다.
     * 없는 경로는 500이 아니라 404로 응답·기록돼야 한다.
     */
    @Nested
    @DisplayName("요청 로그 - 실제 필터 순서")
    class RequestLogging {

        private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        @BeforeEach
        void attach() {
            logger.setLevel(Level.INFO);
            appender.start();
            logger.addAppender(appender);
        }

        @AfterEach
        void detach() {
            logger.detachAppender(appender);
            logger.setLevel(null);
        }

        private Map<String, Object> lastRequestLog() {
            assertThat(appender.list).isNotEmpty();
            Map<String, Object> map = new HashMap<>();
            ILoggingEvent event = appender.list.get(appender.list.size() - 1);
            if (event.getKeyValuePairs() != null) {
                for (KeyValuePair p : event.getKeyValuePairs()) {
                    map.put(p.key, p.value);
                }
            }
            return map;
        }

        @Test
        @DisplayName("토큰 없이 보호 경로 → 401이 경로 템플릿 없이 남는다")
        void logs_401_from_entry_point() throws Exception {
            mockMvc.perform(get("/api/v1/admin/accounts/me")).andExpect(status().isUnauthorized());

            Map<String, Object> log = lastRequestLog();
            assertThat(log).containsEntry("status", 401).containsEntry("method", "GET");
            assertThat(log.get("path")).isNull();
        }

        @Test
        @DisplayName("USER 토큰으로 관리자 경로 → 403과 사용자 식별자가 남는다")
        void logs_403_with_user() throws Exception {
            String userAccessToken = jwtProvider.generateUserAccessToken(999L);

            mockMvc.perform(get("/api/v1/admin/accounts/me")
                            .header("Authorization", "Bearer " + userAccessToken))
                    .andExpect(status().isForbidden());

            assertThat(lastRequestLog()).containsEntry("status", 403).containsEntry("user", "USER:999");
        }

        @Test
        @DisplayName("관리자 토큰으로 성공 → 경로 템플릿과 ADMIN 식별자가 남는다")
        void logs_200_with_template_and_admin() throws Exception {
            saveAdmin("requestlog_admin", "Boaz1234!", Admin.Role.TEAM);
            String accessToken = loginAndGetAccessToken("requestlog_admin", "Boaz1234!");

            mockMvc.perform(get("/api/v1/admin/accounts/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());

            Map<String, Object> log = lastRequestLog();
            assertThat(log).containsEntry("status", 200).containsEntry("path", "/api/v1/admin/accounts/me");
            assertThat((String) log.get("user")).startsWith("ADMIN:");
        }

        @Test
        @DisplayName("없는 경로(탐색 봇) → 500이 아니라 404")
        void unknown_path_is_404() throws Exception {
            mockMvc.perform(get("/.env.local"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("NOT_FOUND"));

            Map<String, Object> log = lastRequestLog();
            assertThat(log).containsEntry("status", 404);
            assertThat(log.get("path")).isNull();
        }
    }
}
