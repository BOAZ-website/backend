package com.boaz.backend.domain.admin.controller;

import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.dto.response.AdminIdResponse;
import com.boaz.backend.domain.admin.dto.response.AdminMeResponse;
import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.service.AdminService;
import com.boaz.backend.global.config.JacksonConfig;
import com.boaz.backend.support.AuthFixtures;
import com.boaz.backend.support.PermissionTestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2층 격자 테스트 — 계정 관련 매트릭스 행 × 기본 세트 주체.
 * <p>{@code @PreAuthorize} 문자열 오타는 컴파일 타임에 안 잡힌다 — 이 테스트가 사실상 컴파일러다.
 *
 * <p><b>서비스는 목이다.</b> 여기서 검증하는 것은 2층뿐이다.
 * <p><b>기대값은 구현에서 복사하지 않고 매트릭스에서 전사했다.</b> {@code DefaultPermissions.of(...)}로
 * 계산하면 전사 실수를 그대로 통과시킨다.
 *
 * <p><b>! {@code @EnableMethodSecurity}가 꺼지면 이 테스트는 조용히 초록불이 되지 않는다.</b>
 * 거부 칸이 서비스 목까지 닿아 2xx가 되므로 빨간불이 된다.
 */
@ActiveProfiles("test")
@WebMvcTest(
    value = AdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({PermissionTestSecurityConfig.class, JacksonConfig.class})
class AdminPermissionGridTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AdminService adminService;

    private static final Long SELF_ID = 1L;

    private static final String CREATE_BODY =
            "{\"username\":\"boaz_team2\",\"password\":\"Boaz1234!\",\"role\":\"TEAM\","
            + "\"name\":\"김보아즈\",\"track\":\"ANALYSIS\",\"term\":25,\"team_name\":\"기획팀\"}";
    private static final String UPDATE_BODY = "{\"name\":\"새이름\"}";
    private static final String PASSWORD_BODY =
            "{\"current_password\":\"Boaz1234!\",\"new_password\":\"NewBoaz1234!\"}";

    /**
     * 기본 세트 키 7개 + 권한 0 조합 1개. 마지막 것이 "닫히는 쪽으로 실패"를 실제로 밟는 행이다 —
     * {@code (SUPER, 그룹리더)}는 명시 키에도 없고 {@code SUPER} 폴백도 없어 빈 집합이 된다.
     */
    private record Subject(String label, Admin.Role role, Admin.TeamName team) {

        UsernamePasswordAuthenticationToken auth() {
            return AuthFixtures.adminAuth(SELF_ID, role, team);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record Endpoint(String label, Supplier<MockHttpServletRequestBuilder> request) {

        @Override
        public String toString() {
            return label;
        }
    }

    private static final List<Subject> SUBJECTS = List.of(
            new Subject("(MASTER,서비스운영팀)", Admin.Role.MASTER, Admin.TeamName.서비스운영팀),
            new Subject("(SUPER,대표진)", Admin.Role.SUPER, Admin.TeamName.대표진),
            new Subject("(SUPER,차기대표진)", Admin.Role.SUPER, Admin.TeamName.차기대표진),
            new Subject("(TEAM,서비스운영팀)", Admin.Role.TEAM, Admin.TeamName.서비스운영팀),
            new Subject("(TEAM,운영지원팀)", Admin.Role.TEAM, Admin.TeamName.운영지원팀),
            new Subject("(TEAM,기획팀)폴백", Admin.Role.TEAM, Admin.TeamName.기획팀),
            new Subject("(HOST,그룹리더)", Admin.Role.HOST, Admin.TeamName.그룹리더),
            new Subject("(SUPER,그룹리더)권한0", Admin.Role.SUPER, Admin.TeamName.그룹리더)
    );

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("POST /accounts", () -> post("/api/v1/admin/accounts")
                    .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)),
            new Endpoint("GET /accounts", () -> get("/api/v1/admin/accounts")),
            new Endpoint("GET /accounts/me", () -> get("/api/v1/admin/accounts/me")),
            new Endpoint("GET /accounts/{id}", () -> get("/api/v1/admin/accounts/2")),
            new Endpoint("PATCH /accounts/{id}", () -> patch("/api/v1/admin/accounts/2")
                    .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY)),
            new Endpoint("DELETE /accounts/{id}", () -> delete("/api/v1/admin/accounts/2")),
            new Endpoint("PATCH /accounts/{id}/password", () -> patch("/api/v1/admin/accounts/2/password")
                    .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
    );

    /**
     * 매트릭스 전사. O = 2층 통과, X = 403.
     *
     * <pre>
     *                        POST  GET   GET   GET   PATCH  DEL   PATCH
     *                        /acc  /acc  /me   /{id} /{id}  /{id} /pw
     * 계정 생성/삭제(CD)       ●                             ●
     * 계정 목록 조회(R)              ●
     * 본인 계정 조회(R)                          ●                        ← SELF_READ 또는 READ
     * 본인/타 계정 수정(U)                               ●            ●   ← SELF_WRITE 또는 WRITE
     * </pre>
     *
     * {@code /accounts/me}는 주체 자신을 돌려주는 엔드포인트라 대상 판정이 없어 2층을 붙이지 않았다 —
     * 권한 0인 주체까지 O인 유일한 열이고, 그것이 의도다.
     */
    private static final String[] GRID = {
            //                      POST  GET  /me  {id} PTCH  DEL   pw
            "(MASTER,서비스운영팀)      O     O    O    O    O    O    O",
            "(SUPER,대표진)            X     O    O    O    O    X    O",
            "(SUPER,차기대표진)         X     O    O    O    O    X    O",
            "(TEAM,서비스운영팀)        X     O    O    O    O    X    O",
            "(TEAM,운영지원팀)          X     X    O    O    O    X    O",
            "(TEAM,기획팀)폴백          X     X    O    O    O    X    O",
            "(HOST,그룹리더)           X     X    O    O    O    X    O",
            "(SUPER,그룹리더)권한0      X     X    O    X    X    X    X",
    };

    static Stream<Arguments> grid() {
        return IntStream.range(0, SUBJECTS.size())
                .boxed()
                .flatMap(row -> {
                    Subject subject = SUBJECTS.get(row);
                    String[] cells = GRID[row].trim().split("\\s+");
                    // 첫 칸은 주체 라벨이다. 전사한 라벨이 실제 주체와 어긋나면 여기서 터진다.
                    if (!cells[0].equals(subject.label())) {
                        throw new IllegalStateException(
                                "격자 라벨 불일치: " + cells[0] + " != " + subject.label());
                    }
                    if (cells.length != ENDPOINTS.size() + 1) {
                        throw new IllegalStateException("격자 칸 수 불일치: " + subject.label());
                    }
                    return IntStream.range(0, ENDPOINTS.size())
                            .mapToObj(col -> Arguments.of(subject, ENDPOINTS.get(col),
                                    "O".equals(cells[col + 1])));
                });
    }

    @BeforeEach
    void stubService() {
        Admin target = AuthFixtures.admin(2L, Admin.Role.TEAM, Admin.TeamName.기획팀);
        when(adminService.getAccounts()).thenReturn(List.of());
        when(adminService.createAccount(any(), any())).thenReturn(new AdminIdResponse(2L));
        when(adminService.getMe(any())).thenReturn(AdminMeResponse.from(target));
        when(adminService.getAccount(any(), any(), any())).thenReturn(AdminAccountResponse.from(target));
        when(adminService.updateAccount(any(), any(), any(), any())).thenReturn(new AdminIdResponse(2L));
    }

    @ParameterizedTest(name = "{0} × {1} → {2}")
    @DisplayName("계정 API 2층 격자 — 기본 세트 주체 8종 × 엔드포인트 7개")
    @MethodSource("grid")
    void grid(Subject subject, Endpoint endpoint, boolean allowed) throws Exception {
        var result = mockMvc.perform(endpoint.request().get().with(authentication(subject.auth())));

        if (allowed) {
            result.andExpect(status().is2xxSuccessful());
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
            // 2층에서 끊겼는지 확인한다. 서비스가 불렸다면 판정이 3층으로 새어나간 것이다.
            verifyNoInteractions(adminService);
        }
    }
}
