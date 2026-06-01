package com.boaz.backend.domain.user.controller;

import com.boaz.backend.domain.user.dto.response.PromoteUsersResponse;
import com.boaz.backend.domain.user.service.UserAdminService;
import com.boaz.backend.global.security.UserPrincipal;
import com.boaz.backend.support.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(
    value = UserAdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class UserAdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserAdminService userAdminService;

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("정상 승격 요청 → 200")
    void success() throws Exception {
        when(userAdminService.bulkPromote(any())).thenReturn(PromoteUsersResponse.of(Collections.emptyList()));

        mockMvc.perform(patch("/api/v1/admin/users/promote")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_ids\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("빈 userIds(@NotEmpty) → 400 INVALID_INPUT_VALUE")
    void emptyUserIds() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/promote")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("TC-008 미인증 요청 → 401 TOKEN_NOT_FOUND")
    void unauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_ids\":[1]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-009 User 권한으로 접근 → 403 ACCESS_DENIED")
    void forbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/promote")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_ids\":[1]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
    }
}
