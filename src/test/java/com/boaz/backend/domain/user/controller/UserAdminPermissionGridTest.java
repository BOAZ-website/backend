package com.boaz.backend.domain.user.controller;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.user.service.UserAdminService;
import com.boaz.backend.global.config.JacksonConfig;
import com.boaz.backend.support.AuthFixtures;
import com.boaz.backend.support.PermissionTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2층 격자 테스트 — 합격자 정회원 승격 API × 최종 권한 매트릭스 주체 8종(+ 권한 0).
 * <p>기대값은 최종 권한 매트릭스({@code FINAL_PERMISSION_MATRIX.md})의 "합격자 정회원 승격 (U)" 행에서 전사했다.
 * {@code DefaultPermissions.of(...)}로 계산하지 않는다.
 * <p>요청 본문은 X 칸에서도 유효해야 한다 — {@code @Valid}가 {@code @PreAuthorize}보다 먼저 돈다.
 */
@ActiveProfiles("test")
@WebMvcTest(
    value = UserAdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({PermissionTestSecurityConfig.class, JacksonConfig.class})
class UserAdminPermissionGridTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserAdminService userAdminService;

    private static final String PROMOTE_BODY = "{\"user_ids\":[1,2]}";

    // 매트릭스 행: 합격자 정회원 승격 (U) — MEMBER_PROMOTION_WRITE
    static Stream<Arguments> grid() {
        return Stream.of(
                Arguments.of("서운팀장(MASTER)", Admin.Role.MASTER, Admin.TeamName.서비스운영팀, true),
                Arguments.of("대표진(SUPER)", Admin.Role.SUPER, Admin.TeamName.대표진, false),
                Arguments.of("차기대표진(SUPER)", Admin.Role.SUPER, Admin.TeamName.차기대표진, false),
                Arguments.of("서비스운영팀(TEAM)", Admin.Role.TEAM, Admin.TeamName.서비스운영팀, true),
                Arguments.of("운영지원팀(TEAM)", Admin.Role.TEAM, Admin.TeamName.운영지원팀, false),
                Arguments.of("기타운영진(TEAM,기획팀)", Admin.Role.TEAM, Admin.TeamName.기획팀, false),
                // 스터디장·ADV팀장 두 열은 코드에서 (HOST, 그룹리더) 한 키다
                Arguments.of("스터디장(HOST)", Admin.Role.HOST, Admin.TeamName.그룹리더, false),
                Arguments.of("ADV팀장(HOST)", Admin.Role.HOST, Admin.TeamName.그룹리더, false),
                Arguments.of("권한0(SUPER,그룹리더)", Admin.Role.SUPER, Admin.TeamName.그룹리더, false)
        );
    }

    @ParameterizedTest(name = "{0} → {3}")
    @DisplayName("합격자 승격 API 2층 격자 — 최종 매트릭스 주체 8종(+권한0)")
    @MethodSource("grid")
    void grid(String label, Admin.Role role, Admin.TeamName team, boolean allowed) throws Exception {
        var result = mockMvc.perform(patch("/api/v1/admin/users/promote")
                .contentType(MediaType.APPLICATION_JSON).content(PROMOTE_BODY)
                .with(authentication(AuthFixtures.adminAuth(1L, role, team))));

        if (allowed) {
            result.andExpect(status().isOk());
            verify(userAdminService).bulkPromote(List.of(1L, 2L));
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
            verifyNoInteractions(userAdminService);
        }
    }
}
