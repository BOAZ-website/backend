package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.recruitment.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CsvService {

    private final ObjectMapper objectMapper;

    private static final List<String> BASE_HEADERS = List.of(
            "지원 부문", "성명", "이메일 주소", "전화번호", "대학교", "본전공",
            "복수/부전공", "마지막 재학 학기", "병역 이수 여부", "생년월일",
            "졸업 예정 시점", "대학원 진학 여부", "지원서 제출 일시"
    );

    public byte[] generateCsv(
            List<Applicant> applicants,
            List<ApplicationQuestion> questions,
            List<ApplicantAnswer> answers
    ) throws IOException {

        // 질문 ID 순서 리스트
        List<String> questionIds = questions.stream()
                .map(ApplicationQuestion::getId)
                .toList();

        // 지원자별 답변 맵 구성 (applicantId -> (questionId -> answer))
        Map<Long, Map<String, ApplicantAnswer>> answerMap = new HashMap<>();
        for (ApplicantAnswer answer : answers) {
            Long applicantId = answer.getApplicant().getId();
            answerMap.computeIfAbsent(applicantId, k -> new HashMap<>())
                    .put(answer.getQuestion().getId(), answer);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // UTF-8 BOM 추가
        baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        try (OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            // 헤더 작성
            List<String> headers = new ArrayList<>(BASE_HEADERS);
            headers.addAll(questionIds);
            writer.write(toCsvRow(headers));

            // 지원자별 행 작성
            for (Applicant applicant : applicants) {
                List<String> row = new ArrayList<>();
                row.add(applicant.getTrack().name());
                row.add(applicant.getName());
                row.add(applicant.getEmail());
                row.add(applicant.getPhone());
                row.add(applicant.getUniversity());
                row.add(applicant.getMajor());
                row.add(formatMinorDoubleMajor(applicant.getMinorDoubleMajor()));
                row.add(String.valueOf(applicant.getLastSemester()));
                row.add(applicant.getMilitaryStatus().name());
                row.add(applicant.getBirthDate().toString());
                row.add(applicant.getGraduationDate());
                row.add(applicant.getGradSchoolPlan() ? "Y" : "N");
                row.add(applicant.getCreatedAt().toString());

                Map<String, ApplicantAnswer> applicantAnswers = answerMap.getOrDefault(applicant.getId(), Collections.emptyMap());
                for (String questionId : questionIds) {
                    ApplicantAnswer answer = applicantAnswers.get(questionId);
                    row.add(answer != null ? formatAnswer(answer) : "");
                }
                writer.write(toCsvRow(row));
            }
        }
        return baos.toByteArray();
    }

    private String formatMinorDoubleMajor(String minorDoubleMajorJson) {
        if (minorDoubleMajorJson == null) return "";
        try {
            JsonNode node = objectMapper.readTree(minorDoubleMajorJson);
            List<String> values = new ArrayList<>();
            node.forEach(n -> values.add(n.asText()));
            return String.join(", ", values);
        } catch (Exception e) {
            return minorDoubleMajorJson;
        }
    }

    private String formatAnswer(ApplicantAnswer answer) {
        if (answer.getAnswerText() != null) {
            return answer.getAnswerText();
        }
        if (answer.getAnswerJson() != null) {
            try {
                JsonNode node = objectMapper.readTree(answer.getAnswerJson());
                StringBuilder sb = new StringBuilder();
                node.fields().forEachRemaining(entry ->
                        sb.append(entry.getKey()).append(": ").append(entry.getValue().asText()).append("\n")
                );
                return sb.toString().trim();
            } catch (Exception e) {
                return answer.getAnswerJson();
            }
        }
        return "";
    }

    private String toCsvRow(List<String> values) {
        return values.stream()
                .map(this::escapeCsvValue)
                .collect(Collectors.joining(",")) + "\n";
    }

    private String escapeCsvValue(String value) {
        if (value == null) return "\"\"";
        // 쌍따옴표, 쉼표, 줄바꿈이 포함된 경우 쌍따옴표로 감싸기
        if (value.contains("\"") || value.contains(",") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return "\"" + value + "\"";
    }
}