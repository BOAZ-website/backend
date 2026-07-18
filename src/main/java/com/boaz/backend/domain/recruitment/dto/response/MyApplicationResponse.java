package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.global.common.enums.MilitaryStatus;
import com.boaz.backend.global.common.enums.Track;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class MyApplicationResponse {

    @Schema(description = "지원자 ID", example = "42")
    @JsonProperty("applicant_id")
    private final Long applicantId;

    @Schema(description = "지원서 상태", example = "DRAFT")
    private final Applicant.ApplicantStatus status;

    @Schema(description = "지원 부문", example = "ENGINEERING")
    private final Track track;

    @Schema(description = "성명", example = "홍길동")
    private final String name;

    @Schema(description = "이메일", example = "hong@example.com")
    private final String email;

    @Schema(description = "전화번호", example = "01012345678")
    private final String phone;

    @Schema(description = "대학교", example = "한국대학교")
    private final String university;

    @Schema(description = "본전공", example = "컴퓨터공학")
    private final String major;

    @Schema(description = "부전공/복수전공 목록")
    @JsonProperty("minor_double_major")
    private final List<String> minorDoubleMajor;

    @Schema(description = "마지막 재학 학기", example = "6")
    @JsonProperty("last_semester")
    private final Integer lastSemester;

    @Schema(description = "병역 상태", example = "COMPLETED_OR_EXEMPT")
    @JsonProperty("military_status")
    private final MilitaryStatus militaryStatus;

    @Schema(description = "생년월일", example = "2000-01-01")
    @JsonProperty("birth_date")
    private final LocalDate birthDate;

    @Schema(description = "졸업 예정 시점", example = "2026-02")
    @JsonProperty("graduation_date")
    private final String graduationDate;

    @Schema(description = "대학원 진학 여부", example = "false")
    @JsonProperty("grad_school_plan")
    private final Boolean gradSchoolPlan;

    @Schema(description = "지원서 답변 목록")
    private final List<AnswerItemResponse> answers;

    @Schema(description = "최초 생성 일시")
    @JsonProperty("created_at")
    private final LocalDateTime createdAt;

    @Schema(description = "마지막 수정 일시")
    @JsonProperty("updated_at")
    private final LocalDateTime updatedAt;

    private MyApplicationResponse(Long applicantId, Applicant.ApplicantStatus status, Track track,
                                   String name, String email, String phone, String university, String major,
                                   List<String> minorDoubleMajor, Integer lastSemester,
                                   MilitaryStatus militaryStatus, LocalDate birthDate,
                                   String graduationDate, Boolean gradSchoolPlan,
                                   List<AnswerItemResponse> answers,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.applicantId = applicantId;
        this.status = status;
        this.track = track;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.university = university;
        this.major = major;
        this.minorDoubleMajor = minorDoubleMajor;
        this.lastSemester = lastSemester;
        this.militaryStatus = militaryStatus;
        this.birthDate = birthDate;
        this.graduationDate = graduationDate;
        this.gradSchoolPlan = gradSchoolPlan;
        this.answers = answers;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MyApplicationResponse of(Applicant applicant, List<String> minorDoubleMajor,
                                            List<AnswerItemResponse> answers) {
        return new MyApplicationResponse(
                applicant.getId(),
                applicant.getStatus(),
                applicant.getTrack(),
                applicant.getName(),
                applicant.getEmail(),
                applicant.getPhone(),
                applicant.getUniversity(),
                applicant.getMajor(),
                minorDoubleMajor,
                applicant.getLastSemester(),
                applicant.getMilitaryStatus(),
                applicant.getBirthDate(),
                applicant.getGraduationDate(),
                applicant.getGradSchoolPlan(),
                answers,
                applicant.getCreatedAt(),
                applicant.getUpdatedAt()
        );
    }

    @Getter
    public static class AnswerItemResponse {

        @Schema(description = "질문 ID", example = "1")
        @JsonProperty("question_id")
        private final Long questionId;

        @Schema(description = "답변 (TEXT: 문자열, TABLE: 객체)")
        private final JsonNode answer;

        public AnswerItemResponse(Long questionId, JsonNode answer) {
            this.questionId = questionId;
            this.answer = answer;
        }
    }
}
