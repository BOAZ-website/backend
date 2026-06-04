package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvServiceTest {

    private CsvService csvService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        csvService = new CsvService(objectMapper);
    }

    private Recruitment makeRecruitment() {
        Recruitment r = Recruitment.create(27,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), "{}", null);
        ReflectionTestUtils.setField(r, "id", 1L);
        return r;
    }

    private Applicant makeApplicant(Recruitment r) {
        User user = User.builder()
                .provider("kakao").providerId("test-1")
                .nickname("홍길동").memberType(MemberType.OUTSIDER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Applicant a = Applicant.builder()
                .recruitment(r)
                .user(user)
                .status(Applicant.ApplicantStatus.SUBMITTED)
                .track(Track.ENGINEERING)
                .name("홍길동").email("hong@example.com")
                .phone("01012345678").university("한국대")
                .major("컴공").build();
        a.markSubmitted();
        ReflectionTestUtils.setField(a, "id", 1L);
        return a;
    }

    private ApplicationQuestion makeQuestion(Recruitment r, ApplicationQuestion.Type type, String label, String metadata) {
        ApplicationQuestion q = ApplicationQuestion.create(
                r, label, ApplicationQuestion.Category.COMMON, type, "질문", null,
                null, metadata, 1, true);
        ReflectionTestUtils.setField(q, "id", 1L);
        return q;
    }

    private ApplicantAnswer makeTextAnswer(Applicant a, ApplicationQuestion q, String text) {
        ApplicantAnswer ans = ApplicantAnswer.builder()
                .applicant(a).question(q).answerText(text).build();
        return ans;
    }

    private ApplicantAnswer makeJsonAnswer(Applicant a, ApplicationQuestion q, String json) {
        ApplicantAnswer ans = ApplicantAnswer.builder()
                .applicant(a).question(q).answerJson(json).build();
        return ans;
    }

    private String generateAndExtractCell(Applicant applicant, ApplicationQuestion question, ApplicantAnswer answer)
            throws IOException {
        byte[] csv = csvService.generateCsv(List.of(applicant), List.of(question), List.of(answer));
        String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\r", "");
        String dataRow = content.split("\n")[1];
        // 마지막 셀(질문 답변 컬럼)을 추출
        String[] cells = dataRow.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        String last = cells[cells.length - 1].trim();
        if (last.startsWith("\"") && last.endsWith("\"")) {
            last = last.substring(1, last.length() - 1).replace("\"\"", "\"");
        }
        return last;
    }

    @Nested
    @DisplayName("formatAnswer — TEXT 답변")
    class TextAnswer {

        @Test
        @DisplayName("TEXT 답변은 그대로 출력")
        void textPassThrough() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TEXT, "TEXT1", null);
            ApplicantAnswer ans = makeTextAnswer(a, q, "내 답변입니다");

            assertThat(generateAndExtractCell(a, q, ans)).isEqualTo("내 답변입니다");
        }
    }

    @Nested
    @DisplayName("formatAnswer — TABLE 단일선택")
    class SingleSelectTable {

        @Test
        @DisplayName("단일선택 TABLE → '키: 값' 형태로 출력")
        void singleSelectFormat() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TABLE, "TABLE1",
                    "{\"rows\":[\"Python\"],\"columns\":[\"경험 없음\"]}");
            ApplicantAnswer ans = makeJsonAnswer(a, q, "{\"Python\":\"경험 없음\"}");

            assertThat(generateAndExtractCell(a, q, ans)).isEqualTo("Python: 경험 없음");
        }

        @Test
        @DisplayName("단일선택 TABLE 복수 행 → 줄바꿈으로 구분")
        void multiRowSingleSelect() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TABLE, "TABLE1",
                    "{\"rows\":[\"Python\",\"R\"],\"columns\":[\"경험 없음\",\"사용 중\"]}");
            ApplicantAnswer ans = makeJsonAnswer(a, q, "{\"Python\":\"경험 없음\",\"R\":\"사용 중\"}");

            String cell = generateAndExtractCell(a, q, ans);
            assertThat(cell).contains("Python: 경험 없음");
            assertThat(cell).contains("R: 사용 중");
        }
    }

    @Nested
    @DisplayName("formatAnswer — TABLE 복수선택")
    class MultiSelectTable {

        @Test
        @DisplayName("복수선택 배열 값 → '키: 값1, 값2' 형태로 join 출력")
        void multiSelectJoinFormat() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TABLE, "MULTI1",
                    "{\"rows\":[\"1월 4일\"],\"columns\":[\"12:00~14:00\",\"14:00~16:00\"],\"multiple\":true}");
            ApplicantAnswer ans = makeJsonAnswer(a, q,
                    "{\"1월 4일\":[\"12:00~14:00\",\"14:00~16:00\"]}");

            assertThat(generateAndExtractCell(a, q, ans))
                    .isEqualTo("1월 4일: 12:00~14:00, 14:00~16:00");
        }

        @Test
        @DisplayName("복수선택 빈 배열 행 → '키: ' 형태로 출력")
        void emptyArrayRow() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TABLE, "MULTI1",
                    "{\"rows\":[\"1월 4일\"],\"columns\":[\"12:00~14:00\"],\"multiple\":true}");
            ApplicantAnswer ans = makeJsonAnswer(a, q, "{\"1월 4일\":[]}");

            assertThat(generateAndExtractCell(a, q, ans)).isEqualTo("1월 4일: ");
        }

        @Test
        @DisplayName("복수선택 단일 원소 → 쉼표 없이 출력")
        void singleElementNoComma() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TABLE, "MULTI1",
                    "{\"rows\":[\"1월 4일\"],\"columns\":[\"12:00~14:00\"],\"multiple\":true}");
            ApplicantAnswer ans = makeJsonAnswer(a, q, "{\"1월 4일\":[\"12:00~14:00\"]}");

            assertThat(generateAndExtractCell(a, q, ans)).isEqualTo("1월 4일: 12:00~14:00");
        }

        @Test
        @DisplayName("행(key)이 '='로 시작하면 CSV 인젝션 sanitize 적용")
        void csvInjectionSanitizedAfterJoin() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TABLE, "MULTI1",
                    "{\"rows\":[\"=날짜\"],\"columns\":[\"12:00~14:00\"],\"multiple\":true}");
            // key가 '='로 시작 → 셀 전체가 "=날짜: ..."로 시작 → sanitize 대상
            ApplicantAnswer ans = makeJsonAnswer(a, q, "{\"=날짜\":[\"12:00~14:00\"]}");

            byte[] csv = csvService.generateCsv(List.of(a), List.of(q), List.of(ans));
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
            // sanitize 후 "=날짜:"로 시작하는 셀이 없어야 함 (앞에 ' 추가됨)
            assertThat(content).doesNotContain("\"=날짜:");
            assertThat(content).contains("'=날짜:");
        }
    }
}
