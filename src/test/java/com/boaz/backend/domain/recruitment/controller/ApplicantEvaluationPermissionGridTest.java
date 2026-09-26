package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.global.config.JacksonConfig;
import com.boaz.backend.global.security.authz.Permission;
import com.boaz.backend.support.AuthFixtures;
import com.boaz.backend.support.PermissionTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2층 격자 테스트 — 지원서 평가 API 8개 × 최종 권한 매트릭스 주체 8종(+ 권한 0).
 * <p>{@code @PreAuthorize} 문자열 오타는 컴파일 타임에 안 잡힌다 — 이 테스트가 사실상 컴파일러다.
 *
 * <p><b>서비스는 목이다.</b> 여기서 검증하는 것은 2층뿐이다. 대상 지원자의 부문 범위(3층, {@code ScopeGuard})는
 * {@code RecruitmentEvaluationServiceTest}·{@code ApplicantEvaluationIntegrationTest}가 본다.
 * <p><b>기대값은 구현에서 복사하지 않고 최종 권한 매트릭스({@code FINAL_PERMISSION_MATRIX.md})에서 전사했다.</b>
 *
 * <p><b>요청 본문은 X 칸에서도 유효해야 한다.</b> {@code @Valid}가 {@code @PreAuthorize}보다 먼저 돈다.
 */
@ActiveProfiles("test")
@WebMvcTest(
    value = ApplicantEvaluationAdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({PermissionTestSecurityConfig.class, JacksonConfig.class})
class ApplicantEvaluationPermissionGridTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RecruitmentService recruitmentService;

    private static final String BASE = "/api/v1/admin/recruitment";

    private static final String FINAL_DECISION_BODY = "{\"final_decision\":\"PASS\"}";
    private static final String EVALUATION_BODY = "{\"decision\":\"PASS\",\"score\":5}";

    /**
     * 최종 매트릭스의 주체 열 8개 + 권한 0 조합 1개.
     * 스터디장·ADV팀장 두 열은 코드에서 {@code (HOST, 그룹리더)} 한 키라 같은 픽스처를 쓴다.
     */
    private record Subject(String label, Admin.Role role, Admin.TeamName team) {

        UsernamePasswordAuthenticationToken auth() {
            return AuthFixtures.adminAuth(1L, role, team);
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
            new Subject("서운팀장(MASTER)", Admin.Role.MASTER, Admin.TeamName.서비스운영팀),
            new Subject("대표진(SUPER)", Admin.Role.SUPER, Admin.TeamName.대표진),
            new Subject("차기대표진(SUPER)", Admin.Role.SUPER, Admin.TeamName.차기대표진),
            new Subject("서비스운영팀(TEAM)", Admin.Role.TEAM, Admin.TeamName.서비스운영팀),
            new Subject("운영지원팀(TEAM)", Admin.Role.TEAM, Admin.TeamName.운영지원팀),
            new Subject("기타운영진(TEAM,기획팀)", Admin.Role.TEAM, Admin.TeamName.기획팀),
            new Subject("스터디장(HOST)", Admin.Role.HOST, Admin.TeamName.그룹리더),
            new Subject("ADV팀장(HOST)", Admin.Role.HOST, Admin.TeamName.그룹리더),
            new Subject("권한0(SUPER,그룹리더)", Admin.Role.SUPER, Admin.TeamName.그룹리더)
    );

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("GET /{rid}/applicants", () -> get(BASE + "/1/applicants")),
            new Endpoint("GET /{rid}/applicants/evaluations", () -> get(BASE + "/1/applicants/evaluations")),
            new Endpoint("PATCH /applicants/{id}/final-decision", () -> patch(BASE + "/applicants/101/final-decision")
                    .contentType(MediaType.APPLICATION_JSON).content(FINAL_DECISION_BODY)),
            new Endpoint("GET /applicants/{id}/evaluations", () -> get(BASE + "/applicants/101/evaluations")),
            new Endpoint("GET /applicants/{id}/interview-questions",
                    () -> get(BASE + "/applicants/101/interview-questions")),
            new Endpoint("GET /applicants/{id}/answers", () -> get(BASE + "/applicants/101/answers")),
            new Endpoint("GET /applicants/{id}/evaluations/me", () -> get(BASE + "/applicants/101/evaluations/me")),
            new Endpoint("PUT /applicants/{id}/evaluations/me", () -> put(BASE + "/applicants/101/evaluations/me")
                    .contentType(MediaType.APPLICATION_JSON).content(EVALUATION_BODY))
    );

    /**
     * 최종 매트릭스 전사. O = 2층 통과, X = 403.
     *
     * <pre>
     * 열 → 매트릭스 행 / permission
     *   APPL, EVS, IQ, ANS, MY, SAVE   본인 부문 서류 평가 CRUD ∪ 다른 부문 서류 평가 CRUD
     *                                  EVALUATION_OWN_TRACK_WRITE 또는 EVALUATION_ALL_TRACK_WRITE
     *   DASH                           위 평가 권한 ∩ 최종 합불 조회 (R) — FINAL_DECISION_READ
     *                                  (응답에 final_decision 이 실린다)
     *   FINAL                          최종 합불 CUD — FINAL_DECISION_WRITE
     * </pre>
     * 부문(본인/전체)은 여기서 가르지 않는다. 2층은 "평가 권한이 둘 중 하나라도 있나"까지만 본다.
     */
    private static final String[] GRID = {
            //                         APPL DASH FINAL EVS  IQ   ANS  MY   SAVE
            "서운팀장(MASTER)            X    X    X     X    X    X    X    X",
            "대표진(SUPER)               O    O    O     O    O    O    O    O",
            "차기대표진(SUPER)            O    O    O     O    O    O    O    O",
            "서비스운영팀(TEAM)           O    O    X     O    O    O    O    O",
            "운영지원팀(TEAM)             O    O    X     O    O    O    O    O",
            "기타운영진(TEAM,기획팀)       O    O    X     O    O    O    O    O",
            "스터디장(HOST)              X    X    X     X    X    X    X    X",
            "ADV팀장(HOST)               X    X    X     X    X    X    X    X",
            "권한0(SUPER,그룹리더)        X    X    X     X    X    X    X    X",
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

    @ParameterizedTest(name = "{0} × {1} → {2}")
    @DisplayName("지원서 평가 API 2층 격자 — 최종 매트릭스 주체 8종(+권한0) × 엔드포인트 8개")
    @MethodSource("grid")
    void grid(Subject subject, Endpoint endpoint, boolean allowed) throws Exception {
        var result = mockMvc.perform(endpoint.request().get().with(authentication(subject.auth())));

        if (allowed) {
            result.andExpect(status().is2xxSuccessful());
            assertThat(mockingDetails(recruitmentService).getInvocations()).isNotEmpty();
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
            // 2층에서 끊겼는지 확인한다. 서비스가 불렸다면 판정이 3층으로 새어나간 것이다.
            verifyNoInteractions(recruitmentService);
        }
    }

    @Test
    @DisplayName("[정상] 컨트롤러는 AdminUserDetails 의 유효 권한을 그대로 서비스에 넘긴다 (3층 판정 재료)")
    void passesPermissionsToService() throws Exception {
        // 오버라이드가 섞인 주체 — 기본 세트가 아니라 넘긴 집합이 그대로 서비스까지 가야 한다
        Admin admin = AuthFixtures.admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀);
        Set<Permission> granted = Set.of(
                Permission.EVALUATION_OWN_TRACK_WRITE, Permission.EVALUATION_ALL_TRACK_WRITE);

        mockMvc.perform(put(BASE + "/applicants/101/evaluations/me")
                        .contentType(MediaType.APPLICATION_JSON).content(EVALUATION_BODY)
                        .with(authentication(AuthFixtures.adminAuth(admin, granted))))
                .andExpect(status().isOk());

        verify(recruitmentService).saveMyEvaluation(
                eq(101L), any(), argThat(a -> a.getId().equals(1L)), eq(granted));
    }

    @Test
    @DisplayName("[인가실패] 평가 권한은 있어도 최종 합불 조회 권한이 없으면 평가 대시보드 → 403")
    void dashboardRequiresFinalDecisionRead() throws Exception {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀);
        Set<Permission> revoked = Set.of(Permission.EVALUATION_OWN_TRACK_WRITE);   // FINAL_DECISION_READ 회수

        mockMvc.perform(get(BASE + "/1/applicants/evaluations")
                        .with(authentication(AuthFixtures.adminAuth(admin, revoked))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        verifyNoInteractions(recruitmentService);
    }
}
