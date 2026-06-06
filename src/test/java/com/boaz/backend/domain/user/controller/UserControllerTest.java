package com.boaz.backend.domain.user.controller;

import com.boaz.backend.domain.user.dto.response.UserInfoResponse;
import com.boaz.backend.domain.user.service.UserService;
import com.boaz.backend.global.security.UserPrincipal;
import com.boaz.backend.support.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(
    value = UserController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("TC-006 정상 조회 → 200 + nickname")
    void success() throws Exception {
        when(userService.getMyInfo(1L)).thenReturn(UserInfoResponse.builder().nickname("홍길동").build());

        mockMvc.perform(get("/api/v1/users/me").with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.nickname").value("홍길동"));
    }

    @Test
    @DisplayName("TC-003 미인증 요청 → 401 TOKEN_NOT_FOUND")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));
    }
}
