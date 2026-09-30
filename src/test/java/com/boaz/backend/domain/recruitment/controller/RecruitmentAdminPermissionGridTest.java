package com.boaz.backend.domain.recruitment.controller;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2층 격자 테스트 — 리크루팅 관리 API 12개 × 최종 권한 매트릭스 주체 8종(+ 권한 0).
 * <p>{@code @PreAuthorize} 문자열 오타는 컴파일 타임에 안 잡힌다 — 이 테스트가 사실상 컴파일러다.
 *
 * <p><b>서비스는 목이다.</b> 여기서 검증하는 것은 2층뿐이다.
 * <p><b>기대값은 구현에서 복사하지 않고 최종 권한 매트릭스({@code FINAL_PERMISSION_MATRIX.md})에서 전사했다.</b>
 * {@code DefaultPermissions.of(...)}로 계산하면 전사 실수를 그대로 통과시킨다.
 *
 * <p><b>요청 본문과 파라미터는 X 칸에서도 유효해야 한다.</b> {@code @Valid}·파라미터 바인딩이
 * {@code @PreAuthorize}보다 먼저 돌기 때문에, 잘못된 요청이면 403 이 아니라 400 이 나와 판정을 가린다.
 */
@ActiveProfiles("test")
@WebMvcTest(
    value = RecruitmentAdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({PermissionTestSecurityConfig.class, JacksonConfig.class})
class RecruitmentAdminPermissionGridTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RecruitmentService recruitmentService;

    private static final String BASE = "/api/v1/admin/recruitment";

    private static final String RECRUITMENT_CREATE_BODY =
            "{\"term\":28,\"start_date\":\"2026-08-01T00:00:00\",\"end_date\":\"2026-08-15T23:59:59\","
            + "\"schedule\":[],\"brochure_url\":\"https://e.com/b.pdf\"}";
    private static final String RECRUITMENT_UPDATE_BODY = "{\"start_date\":\"2026-08-01T00:00:00\"}";
    private static final String QUESTIONS_CREATE_BODY =
            "{\"recruitment_id\":1,\"questions\":[{\"label\":\"공통1\",\"category\":\"COMMON\",\"type\":\"TEXT\","
            + "\"content\":\"질문\",\"limit_length\":500,\"order_num\":1,\"is_required\":true}]}";
    private static final String QUESTION_UPDATE_BODY = "{\"content\":\"수정된 내용\"}";

    /**
     * 최종 매트릭스의 주체 열 8개 + 권한 0 조합 1개.
     * 스터디장·ADV팀장 두 열은 코드에서 {@code (HOST, 그룹리더)} 한 키라 같은 픽스처를 쓴다 —
     * 매트릭스 열이므로 라벨은 나눠 둔다. 마지막 행은 매트릭스에 없는 조합이 권한 0으로 닫히는지 본다.
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
            new Endpoint("GET /", () -> get(BASE)),
            new Endpoint("POST /", () -> post(BASE)
                    .contentType(MediaType.APPLICATION_JSON).content(RECRUITMENT_CREATE_BODY)),
            new Endpoint("PATCH /{id}", () -> patch(BASE + "/1")
                    .contentType(MediaType.APPLICATION_JSON).content(RECRUITMENT_UPDATE_BODY)),
            new Endpoint("DELETE /{id}", () -> delete(BASE + "/1")),
            new Endpoint("POST /applications/download", () -> post(BASE + "/applications/download")
                    .param("term", "26")),
            new Endpoint("DELETE /{rid}/applicants", () -> delete(BASE + "/1/applicants")),
            new Endpoint("GET /{rid}/questions", () -> get(BASE + "/1/questions")),
            new Endpoint("POST /questions", () -> post(BASE + "/questions")
                    .contentType(MediaType.APPLICATION_JSON).content(QUESTIONS_CREATE_BODY)),
            new Endpoint("PATCH /questions/{qid}", () -> patch(BASE + "/questions/1")
                    .contentType(MediaType.APPLICATION_JSON).content(QUESTION_UPDATE_BODY)),
            new Endpoint("DELETE /questions/{qid}", () -> delete(BASE + "/questions/1")),
            new Endpoint("GET /subscriptions", () -> get(BASE + "/subscriptions")),
            new Endpoint("DELETE /subscriptions", () -> delete(BASE + "/subscriptions"))
    );

    /**
     * 최종 매트릭스 전사. O = 2층 통과, X = 403.
     *
     * <pre>
     * 열 → 매트릭스 행 / permission
     *   GET /, GET /{rid}/questions           리크루팅 공고 / 문항 관리 조회 (R)   RECRUITMENT_NOTICE_READ
     *   POST·PATCH·DELETE 공고, 문항 CUD        리크루팅 공고 / 문항 관리 CUD        RECRUITMENT_NOTICE_WRITE
     *   POST /applications/download           지원자 CSV 추출 (R)                 APPLICANT_CSV_READ
     *   DELETE /{rid}/applicants              지원서 전체 삭제                     APPLICATION_DELETE_ALL
     *   GET /subscriptions                    사전알림명단 조회 (R)                PRE_NOTIFICATION_READ
     *   DELETE /subscriptions                 사전알림명단 전체 삭제 (D)           PRE_NOTIFICATION_DELETE
     * </pre>
     */
    private static final String[] GRID = {
            //                         GET  POST PTCH DEL  CSV  DELA GETQ POSQ PTQ  DELQ GETS DELS
            "서운팀장(MASTER)            O    O    O    O    O    O    O    O    O    O    O    O",
            "대표진(SUPER)               O    X    X    X    X    X    O    X    X    X    O    X",
            "차기대표진(SUPER)            O    X    X    X    X    X    O    X    X    X    O    X",
            "서비스운영팀(TEAM)           O    O    O    O    O    O    O    O    O    O    O    O",
            "운영지원팀(TEAM)             X    X    X    X    X    X    X    X    X    X    X    X",
            "기타운영진(TEAM,기획팀)       X    X    X    X    X    X    X    X    X    X    X    X",
            "스터디장(HOST)              X    X    X    X    X    X    X    X    X    X    X    X",
            "ADV팀장(HOST)               X    X    X    X    X    X    X    X    X    X    X    X",
            "권한0(SUPER,그룹리더)        X    X    X    X    X    X    X    X    X    X    X    X",
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
    @DisplayName("리크루팅 관리 API 2층 격자 — 최종 매트릭스 주체 8종(+권한0) × 엔드포인트 12개")
    @MethodSource("grid")
    void grid(Subject subject, Endpoint endpoint, boolean allowed) throws Exception {
        var result = mockMvc.perform(endpoint.request().get().with(authentication(subject.auth())));

        if (allowed) {
            result.andExpect(status().is2xxSuccessful());
            assertThat(mockingDetails(recruitmentService).getInvocations()).isNotEmpty();
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
            // 2층에서 끊겼는지 확인한다. 서비스가 불렸다면 판정이 새어나간 것이다.
            verifyNoInteractions(recruitmentService);
        }
    }
}
