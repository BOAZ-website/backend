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
        return makeApplicant(r, "홍길동", "한국대", "컴공");
    }

    private Applicant makeApplicant(Recruitment r, String name, String university, String major) {
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
                .name(name).email("hong@example.com")
                .phone("01012345678").university(university)
                .major(major).build();
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
    @DisplayName("최종 평가(final_decision) 컬럼")
    class FinalDecisionColumn {

        @Test
        @DisplayName("헤더에 '최종 평가' 컬럼이 '전화번호' 다음에 포함")
        void headerContainsFinalDecision() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);

            byte[] csv = csvService.generateCsv(List.of(a), List.of(), List.of());
            String header = new String(csv, java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll("\r", "").split("\n")[0];
            String[] cols = header.split(",", -1);

            assertThat(header).contains("최종 평가");
            // 전화번호(index 4) 바로 뒤가 최종 평가(index 5)
            assertThat(unquote(cols[4])).isEqualTo("전화번호");
            assertThat(unquote(cols[5])).isEqualTo("최종 평가");
        }

        @Test
        @DisplayName("final_decision=PASS → '합격', FAIL → '불합격' 한글 출력")
        void decisionRenderedInKorean() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant pass = makeApplicant(r);
            pass.updateFinalDecision(com.boaz.backend.domain.recruitment.entity.EvaluationDecision.PASS);

            assertThat(decisionCell(pass)).isEqualTo("합격");

            Applicant fail = makeApplicant(r);
            fail.updateFinalDecision(com.boaz.backend.domain.recruitment.entity.EvaluationDecision.FAIL);
            assertThat(decisionCell(fail)).isEqualTo("불합격");
        }

        @Test
        @DisplayName("기본값(PENDING) → '미정' 출력")
        void defaultPendingRendered() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r); // 기본 final_decision = PENDING

            assertThat(decisionCell(a)).isEqualTo("미정");
        }

        // 데이터 행에서 '최종 평가' 셀(index 5) 추출
        private String decisionCell(Applicant a) throws IOException {
            byte[] csv = csvService.generateCsv(List.of(a), List.of(), List.of());
            String dataRow = new String(csv, java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll("\r", "").split("\n")[1];
            String[] cells = dataRow.split(",", -1);
            return unquote(cells[5]);
        }

        private String unquote(String cell) {
            String c = cell.trim();
            if (c.startsWith("\"") && c.endsWith("\"")) {
                c = c.substring(1, c.length() - 1).replace("\"\"", "\"");
            }
            return c;
        }
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

            // 셀 내부에 \n이 있어 row 파싱이 복잡하므로 raw CSV 직접 확인
            byte[] csv = csvService.generateCsv(List.of(a), List.of(q), List.of(ans));
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content).contains("Python: 경험 없음");
            assertThat(content).contains("R: 사용 중");
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

            // formatAnswer 끝에 trim()이 있어 trailing space 제거됨 → "1월 4일:"
            assertThat(generateAndExtractCell(a, q, ans)).isEqualTo("1월 4일:");
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

    @Nested
    @DisplayName("기본 컬럼 CSV 인젝션 방어 (name/university/major)")
    class BaseColumnCsvInjection {

        @Test
        @DisplayName("name이 '='로 시작 → sanitize 적용되어 앞에 ' 추가")
        void nameStartingWithEquals() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r, "=HYPERLINK(\"http://evil.com\")", "한국대", "컴공");

            byte[] csv = csvService.generateCsv(List.of(a), List.of(), List.of());
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

            assertThat(content).doesNotContain("\"=HYPERLINK");
            assertThat(content).contains("'=HYPERLINK");
        }

        @Test
        @DisplayName("university가 '+'로 시작 → sanitize 적용")
        void universityStartingWithPlus() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r, "홍길동", "+1+1", "컴공");

            byte[] csv = csvService.generateCsv(List.of(a), List.of(), List.of());
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

            assertThat(content).doesNotContain("\"+1+1");
            assertThat(content).contains("'+1+1");
        }

        @Test
        @DisplayName("major가 '-'로 시작 → sanitize 적용")
        void majorStartingWithMinus() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r, "홍길동", "한국대", "-2+3");

            byte[] csv = csvService.generateCsv(List.of(a), List.of(), List.of());
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

            assertThat(content).doesNotContain("\"-2+3");
            assertThat(content).contains("'-2+3");
        }

        @Test
        @DisplayName("major가 '@'로 시작 → sanitize 적용")
        void majorStartingWithAt() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r, "홍길동", "한국대", "@SUM(1,1)");

            byte[] csv = csvService.generateCsv(List.of(a), List.of(), List.of());
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

            assertThat(content).doesNotContain("\"@SUM");
            assertThat(content).contains("'@SUM");
        }

        @Test
        @DisplayName("정상 값(트리거 문자로 시작하지 않음)은 sanitize 미적용")
        void normalValueNotSanitized() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r, "홍길동", "한국대", "컴퓨터공학과");

            byte[] csv = csvService.generateCsv(List.of(a), List.of(), List.of());
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

            assertThat(content).contains("\"컴퓨터공학과\"");
        }
    }

    @Nested
    @DisplayName("빈 지원자 리스트")
    class EmptyApplicantList {

        @Test
        @DisplayName("지원자 없음 → 헤더만 출력, 데이터 행 없음")
        void headerOnly() throws IOException {
            Recruitment r = makeRecruitment();
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TEXT, "TEXT1", null);

            byte[] csv = csvService.generateCsv(List.of(), List.of(q), List.of());
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll("\r", "");
            String[] lines = content.split("\n");

            assertThat(lines).hasSize(1);
            assertThat(lines[0]).contains("TEXT1");
        }
    }

    @Nested
    @DisplayName("값 내 큰따옴표 이스케이프")
    class QuoteEscaping {

        @Test
        @DisplayName("name에 큰따옴표 포함 → 중복 따옴표로 이스케이프됨")
        void quoteInNameIsDoubled() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r, "홍\"길동\"", "한국대", "컴공");

            byte[] csv = csvService.generateCsv(List.of(a), List.of(), List.of());
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

            assertThat(content).contains("\"홍\"\"길동\"\"\"");
        }

        @Test
        @DisplayName("TEXT 답변에 큰따옴표 포함 → 중복 따옴표로 이스케이프됨")
        void quoteInAnswerIsDoubled() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TEXT, "TEXT1", null);
            ApplicantAnswer ans = makeTextAnswer(a, q, "그는 \"최고\"라고 말했다");

            assertThat(generateAndExtractCell(a, q, ans)).isEqualTo("그는 \"최고\"라고 말했다");
        }
    }

    @Nested
    @DisplayName("TEXT 답변 내 개행")
    class TextAnswerWithNewline {

        @Test
        @DisplayName("TEXT 답변에 개행 포함 → 따옴표로 감싸져 하나의 셀로 유지됨")
        void newlineKeptWithinQuotedCell() throws IOException {
            Recruitment r = makeRecruitment();
            Applicant a = makeApplicant(r);
            ApplicationQuestion q = makeQuestion(r, ApplicationQuestion.Type.TEXT, "TEXT1", null);
            ApplicantAnswer ans = makeTextAnswer(a, q, "첫째 줄\n둘째 줄");

            byte[] csv = csvService.generateCsv(List.of(a), List.of(q), List.of(ans));
            String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

            assertThat(content).contains("\"첫째 줄\n둘째 줄\"");
        }
    }
}
