package com.boaz.backend.domain.admin.controller;

import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.dto.response.AdminIdResponse;
import com.boaz.backend.domain.admin.dto.response.AdminMeResponse;
import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.service.AdminService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.config.JacksonConfig;
import com.boaz.backend.global.security.AdminUserDetails;
import com.boaz.backend.global.security.UserPrincipal;
import com.boaz.backend.support.TestSecurityConfig;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(
    value = AdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class AdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AdminService adminService;

    private static final String CREATE_BODY =
            "{\"username\":\"boaz_team2\",\"password\":\"Boaz1234!\",\"role\":\"TEAM\","
            + "\"name\":\"김보아즈\",\"track\":\"ANALYSIS\",\"term\":25,\"team_name\":\"기획팀\"}";

    private Admin admin(Long id, Admin.Role role) {
        Admin a = Admin.builder()
                .username("user" + id).password("ENC").role(role).name("name" + id)
                .track(Track.ANALYSIS).term(25).teamName(Admin.TeamName.기획팀).createdBy(null)
                .build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private UsernamePasswordAuthenticationToken adminAuth(Admin.Role role) {
        AdminUserDetails principal = new AdminUserDetails(admin(1L, role));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return adminAuth(Admin.Role.SUPER);
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ──────────────────────────────────────────────
    // ADMIN-001: GET /api/v1/admin/accounts
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-001 GET /api/v1/admin/accounts")
    class GetAccounts {

        @Test
        @DisplayName("TC-004 SUPER 정상 조회 → 200 + 목록")
        void success() throws Exception {
            when(adminService.getAccounts(any()))
                    .thenReturn(List.of(AdminAccountResponse.from(admin(1L, Admin.Role.SUPER)),
                            AdminAccountResponse.from(admin(2L, Admin.Role.TEAM))));

            mockMvc.perform(get("/api/v1/admin/accounts").with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("EX-002 TEAM 호출 → 서비스 ACCESS_DENIED → 403")
        void teamForbidden() throws Exception {
            when(adminService.getAccounts(any())).thenThrow(new CustomException(ErrorCode.ACCESS_DENIED));

            mockMvc.perform(get("/api/v1/admin/accounts").with(authentication(adminAuth(Admin.Role.TEAM))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("TC-005 미인증 → 401 TOKEN_NOT_FOUND")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/admin/accounts"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] User 권한으로 접근 → 403 ACCESS_DENIED")
        void forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/accounts").with(authentication(userAuth())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-002: POST /api/v1/admin/accounts
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-002 POST /api/v1/admin/accounts")
    class CreateAccount {

        @Test
        @DisplayName("TC-005 정상 생성 → 201 + id")
        void success() throws Exception {
            when(adminService.createAccount(any(), any())).thenReturn(new AdminIdResponse(13L));

            mockMvc.perform(post("/api/v1/admin/accounts")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.id").value(13));
        }

        @Test
        @DisplayName("TC-006 비밀번호 규칙 위반(@Pattern) → 400 INVALID_INPUT_VALUE")
        void invalidPassword() throws Exception {
            String body = CREATE_BODY.replace("Boaz1234!", "1234");

            mockMvc.perform(post("/api/v1/admin/accounts")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("EX-003 username 중복 → 409 DUPLICATE_USERNAME")
        void duplicateUsername() throws Exception {
            when(adminService.createAccount(any(), any()))
                    .thenThrow(new CustomException(ErrorCode.DUPLICATE_USERNAME));

            mockMvc.perform(post("/api/v1/admin/accounts")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DUPLICATE_USERNAME"));
        }

        @Test
        @DisplayName("TC-007 미인증 → 401 TOKEN_NOT_FOUND")
        void unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/admin/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-007: GET /api/v1/admin/accounts/me
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-007 GET /api/v1/admin/accounts/me")
    class GetMe {

        @Test
        @DisplayName("TC-003 인증 주체 → 200 + {id, team_name, name}")
        void success() throws Exception {
            when(adminService.getMe(any()))
                    .thenReturn(AdminMeResponse.builder().id(1L).teamName("대표진").name("문혁준").build());

            mockMvc.perform(get("/api/v1/admin/accounts/me").with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.team_name").value("대표진"))
                    .andExpect(jsonPath("$.data.name").value("문혁준"))
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.role").doesNotExist());
        }

        @Test
        @DisplayName("TEAM 도 호출 가능 → 200")
        void teamAllowed() throws Exception {
            when(adminService.getMe(any()))
                    .thenReturn(AdminMeResponse.builder().id(5L).teamName("기획팀").name("홍길동").build());

            mockMvc.perform(get("/api/v1/admin/accounts/me").with(authentication(adminAuth(Admin.Role.TEAM))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(5));
        }

        @Test
        @DisplayName("TC-004 미인증 → 401 TOKEN_NOT_FOUND")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/admin/accounts/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-003: GET /api/v1/admin/accounts/{id}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-003 GET /api/v1/admin/accounts/{id}")
    class GetAccount {

        @Test
        @DisplayName("정상 조회 → 200")
        void success() throws Exception {
            when(adminService.getAccount(eq(2L), any()))
                    .thenReturn(AdminAccountResponse.from(admin(2L, Admin.Role.TEAM)));

            mockMvc.perform(get("/api/v1/admin/accounts/2").with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(2));
        }

        @Test
        @DisplayName("TC-005 id 타입 불일치 → 400 INVALID_PARAMETER_TYPE")
        void typeMismatch() throws Exception {
            mockMvc.perform(get("/api/v1/admin/accounts/abc").with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_PARAMETER_TYPE"));
        }

        @Test
        @DisplayName("EX-004 존재하지 않는 계정 → 404 ADMIN_NOT_FOUND")
        void notFound() throws Exception {
            when(adminService.getAccount(eq(999L), any()))
                    .thenThrow(new CustomException(ErrorCode.ADMIN_NOT_FOUND));

            mockMvc.perform(get("/api/v1/admin/accounts/999").with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("ADMIN_NOT_FOUND"));
        }

        @Test
        @DisplayName("EX-002 TEAM 타 계정 → 서비스 ACCESS_DENIED → 403")
        void teamOther() throws Exception {
            when(adminService.getAccount(eq(2L), any()))
                    .thenThrow(new CustomException(ErrorCode.ACCESS_DENIED));

            mockMvc.perform(get("/api/v1/admin/accounts/2").with(authentication(adminAuth(Admin.Role.TEAM))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("EX-001 미인증 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/admin/accounts/2"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-004: PATCH /api/v1/admin/accounts/{id}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-004 PATCH /api/v1/admin/accounts/{id}")
    class UpdateAccount {

        @Test
        @DisplayName("TC-009 정상 수정 → 200 + id")
        void success() throws Exception {
            when(adminService.updateAccount(eq(2L), any(), any())).thenReturn(new AdminIdResponse(2L));

            mockMvc.perform(patch("/api/v1/admin/accounts/2")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"새이름\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(2));
        }

        @Test
        @DisplayName("EX-002 본인 role 변경 → 403 CANNOT_MODIFY_OWN_ROLE")
        void cannotModifyOwnRole() throws Exception {
            when(adminService.updateAccount(eq(1L), any(), any()))
                    .thenThrow(new CustomException(ErrorCode.CANNOT_MODIFY_OWN_ROLE));

            mockMvc.perform(patch("/api/v1/admin/accounts/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"TEAM\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("CANNOT_MODIFY_OWN_ROLE"));
        }

        @Test
        @DisplayName("EX-008 미인증 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/accounts/2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-005: DELETE /api/v1/admin/accounts/{id}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-005 DELETE /api/v1/admin/accounts/{id}")
    class DeleteAccount {

        @Test
        @DisplayName("TC-006 정상 삭제 → 200 + data null")
        void success() throws Exception {
            doNothing().when(adminService).deleteAccount(eq(2L), any());

            mockMvc.perform(delete("/api/v1/admin/accounts/2").with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("EX-002 마지막 SUPER 삭제 → 400 LAST_SUPER_ACCOUNT")
        void lastSuper() throws Exception {
            doThrow(new CustomException(ErrorCode.LAST_SUPER_ACCOUNT))
                    .when(adminService).deleteAccount(eq(1L), any());

            mockMvc.perform(delete("/api/v1/admin/accounts/1").with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("LAST_SUPER_ACCOUNT"));
        }

        @Test
        @DisplayName("EX-005 미인증 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/accounts/2"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-006: PATCH /api/v1/admin/accounts/{id}/password
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-006 PATCH /api/v1/admin/accounts/{id}/password")
    class ResetPassword {

        @Test
        @DisplayName("TC-008 정상 변경 → 200 + data null")
        void success() throws Exception {
            doNothing().when(adminService).resetPassword(eq(2L), any(), any());

            mockMvc.perform(patch("/api/v1/admin/accounts/2/password")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"new_password\":\"NewBoaz1234!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("TC-007 newPassword 규칙 위반(@Pattern) → 400 INVALID_INPUT_VALUE")
        void invalidNewPassword() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/accounts/2/password")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"new_password\":\"1234\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("EX-003 본인 변경 currentPassword 불일치 → 401 INVALID_CURRENT_PASSWORD")
        void invalidCurrentPassword() throws Exception {
            doThrow(new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD))
                    .when(adminService).resetPassword(eq(1L), any(), any());

            mockMvc.perform(patch("/api/v1/admin/accounts/1/password")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"current_password\":\"wrong\",\"new_password\":\"NewBoaz1234!\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("INVALID_CURRENT_PASSWORD"));
        }

        @Test
        @DisplayName("EX-007 미인증 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/accounts/2/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"new_password\":\"NewBoaz1234!\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
