package com.boaz.backend.domain.archive.controller;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.archive.service.ArchiveAdminService;
import com.boaz.backend.domain.curriculum.controller.CurriculumAdminController;
import com.boaz.backend.domain.curriculum.service.CurriculumService;
import com.boaz.backend.domain.faq.controller.FaqAdminController;
import com.boaz.backend.domain.faq.service.FaqService;
import com.boaz.backend.domain.review.controller.ReviewAdminController;
import com.boaz.backend.domain.review.service.ReviewService;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2층 격자 테스트: 매트릭스의 {@code 콘텐츠(아카이빙, FAQ, 수료자 후기, 커리큘럼) CUD} 행 × 기본 세트 주체.
 * <p>한 행이 도메인 4개의 admin 컨트롤러에 걸쳐 있어 격자도 하나로 묶는다. 조회는 공개 GET({@code permitAll})뿐이다.
 *
 * <p><b>서비스는 목이다.</b> 여기서 검증하는 것은 2층뿐이다.
 * <p><b>기대값은 구현에서 복사하지 않고 매트릭스에서 직접 옮겨 썼다.</b>
 *
 * <p><b>! {@code @EnableMethodSecurity}가 꺼지면 이 테스트는 조용히 초록불이 되지 않는다.</b>
 * 거부 칸이 서비스 목까지 닿아 2xx가 되므로 빨간불이 된다.
 */
@ActiveProfiles("test")
@WebMvcTest(
    value = {ArchiveAdminController.class, CurriculumAdminController.class,
             FaqAdminController.class, ReviewAdminController.class},
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(PermissionTestSecurityConfig.class)
class ContentPermissionGridTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ArchiveAdminService archiveAdminService;
    @MockitoBean CurriculumService curriculumService;
    @MockitoBean FaqService faqService;
    @MockitoBean ReviewService reviewService;

    private static final String ARCHIVE_CREATE_DATA =
            "{\"term\":8,\"title\":\"AI 수요 예측\",\"track\":\"ANALYSIS\","
            + "\"links\":\"{\\\"slideshare\\\":\\\"https://slideshare.net/boaz\\\"}\",\"content_date\":\"2024-07-01\"}";
    private static final String ARCHIVE_UPDATE_DATA = "{\"title\":\"새 제목\"}";
    private static final String CURRICULUM_CREATE_BODY =
            "{\"track\":\"ANALYSIS\",\"curriculum_steps\":[{\"step\":1,\"title\":\"제목\",\"desc\":\"설명\"}]}";
    private static final String CURRICULUM_UPDATE_BODY =
            "{\"curriculum_steps\":[{\"step\":1,\"title\":\"교체됨\",\"desc\":\"새설명\"}]}";
    private static final String FAQ_CREATE_BODY =
            "{\"question\":\"지원 방법?\",\"answer\":\"홈페이지를 통해 지원\",\"category\":\"RECRUITMENT\",\"order_num\":1}";
    private static final String REVIEW_CREATE_BODY =
            "{\"name\":\"홍길동\",\"track\":\"ANALYSIS\",\"term\":26,\"content\":\"좋은 경험이었습니다.\"}";
    private static final String REVIEW_UPDATE_BODY = "{\"content\":\"수정된 후기\"}";

    /**
     * 기본 세트 키 7개 + 권한 0 조합 1개.
     * {@code (SUPER, 그룹리더)}는 명시 키에도 없고 {@code SUPER} 폴백도 없어 빈 집합이 된다.
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
            archiveCreate("projects"),
            archiveCreate("activities"),
            archiveCreate("blogs"),
            archiveUpdate("projects"),
            archiveUpdate("activities"),
            archiveUpdate("blogs"),
            new Endpoint("DELETE /archiving/projects/{id}", () -> delete("/api/v1/admin/archiving/projects/1")),
            new Endpoint("DELETE /archiving/activities/{id}", () -> delete("/api/v1/admin/archiving/activities/1")),
            new Endpoint("DELETE /archiving/blogs/{id}", () -> delete("/api/v1/admin/archiving/blogs/1")),
            new Endpoint("POST /curriculums", () -> post("/api/v1/admin/curriculums")
                    .contentType(MediaType.APPLICATION_JSON).content(CURRICULUM_CREATE_BODY)),
            new Endpoint("PUT /curriculums/{id}", () -> put("/api/v1/admin/curriculums/1")
                    .contentType(MediaType.APPLICATION_JSON).content(CURRICULUM_UPDATE_BODY)),
            new Endpoint("DELETE /curriculums/{id}", () -> delete("/api/v1/admin/curriculums/1")),
            new Endpoint("POST /faqs", () -> post("/api/v1/admin/faqs")
                    .contentType(MediaType.APPLICATION_JSON).content(FAQ_CREATE_BODY)),
            new Endpoint("PATCH /faqs/{id}", () -> patch("/api/v1/admin/faqs/1")
                    .contentType(MediaType.APPLICATION_JSON).content("{}")),
            new Endpoint("DELETE /faqs/{id}", () -> delete("/api/v1/admin/faqs/1")),
            new Endpoint("POST /reviews", () -> post("/api/v1/admin/reviews")
                    .contentType(MediaType.APPLICATION_JSON).content(REVIEW_CREATE_BODY)),
            new Endpoint("PATCH /reviews/{id}", () -> patch("/api/v1/admin/reviews/1")
                    .contentType(MediaType.APPLICATION_JSON).content(REVIEW_UPDATE_BODY)),
            new Endpoint("DELETE /reviews/{id}", () -> delete("/api/v1/admin/reviews/1"))
    );

    private static Endpoint archiveCreate(String category) {
        return new Endpoint("POST /archiving/" + category, () -> multipart("/api/v1/admin/archiving/" + category)
                .file(jsonPart(ARCHIVE_CREATE_DATA))
                .file(new MockMultipartFile("image", "photo.png", "image/png", new byte[]{1, 2, 3})));
    }

    private static Endpoint archiveUpdate(String category) {
        return new Endpoint("PATCH /archiving/" + category + "/{id}",
                () -> multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/" + category + "/1")
                        .file(jsonPart(ARCHIVE_UPDATE_DATA)));
    }

    private static MockMultipartFile jsonPart(String json) {
        return new MockMultipartFile("data", "", "application/json", json.getBytes());
    }

    /**
     * 매트릭스 전사. O = 2층 통과, X = 403.
     * 콘텐츠 CUD 는 서운팀장과 서비스운영팀만 O 다. 행 하나라 18칸이 주체마다 전부 같다 —
     * 한 칸이라도 다르면 부착이 빠졌거나 다른 permission 이 붙은 것이다.
     *
     * <pre>
     * 열 순서: archiving POST×3(projects·activities·blogs) · PATCH×3 · DELETE×3
     *          · curriculums POST·PUT·DELETE · faqs POST·PATCH·DELETE · reviews POST·PATCH·DELETE
     * </pre>
     */
    private static final String[] GRID = {
            //                      ├──── archiving (9) ────┤ ├cur┤ ├faq┤ ├rev┤
            "(MASTER,서비스운영팀)      O O O O O O O O O  O O O  O O O  O O O",
            "(SUPER,대표진)            X X X X X X X X X  X X X  X X X  X X X",
            "(SUPER,차기대표진)         X X X X X X X X X  X X X  X X X  X X X",
            "(TEAM,서비스운영팀)        O O O O O O O O O  O O O  O O O  O O O",
            "(TEAM,운영지원팀)          X X X X X X X X X  X X X  X X X  X X X",
            "(TEAM,기획팀)폴백          X X X X X X X X X  X X X  X X X  X X X",
            "(HOST,그룹리더)           X X X X X X X X X  X X X  X X X  X X X",
            "(SUPER,그룹리더)권한0      X X X X X X X X X  X X X  X X X  X X X",
    };

    static Stream<Arguments> grid() {
        return IntStream.range(0, SUBJECTS.size())
                .boxed()
                .flatMap(row -> {
                    Subject subject = SUBJECTS.get(row);
                    String[] cells = GRID[row].trim().split("\\s+");
                    // 첫 칸은 주체 라벨이다. 옮겨 쓴 라벨이 실제 주체와 어긋나면 여기서 터진다.
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
    @DisplayName("콘텐츠 API 2층 격자: 기본 세트 주체 8종 × 엔드포인트 18개")
    @MethodSource("grid")
    void grid(Subject subject, Endpoint endpoint, boolean allowed) throws Exception {
        var result = mockMvc.perform(endpoint.request().get().with(authentication(subject.auth())));

        if (allowed) {
            result.andExpect(status().is2xxSuccessful());
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
            // 2층에서 끊겼는지 확인한다. 서비스가 불렸다면 판정이 3층으로 새어나간 것이다.
            verifyNoInteractions(archiveAdminService, curriculumService, faqService, reviewService);
        }
    }
}
