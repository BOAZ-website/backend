package com.boaz.backend.domain.recruitment.dto.request;

import com.boaz.backend.global.common.enums.MilitaryStatus;
import com.boaz.backend.global.common.enums.Track;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;

import java.util.List;

@Getter
public class DraftApplicationRequest {

    @Schema(description = "지원 부문", example = "ENGINEERING", allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private Track track;

    @Schema(description = "성명", example = "홍길동")
    private String name;

    @Schema(description = "이메일", example = "hong@example.com")
    private String email;

    @Schema(description = "전화번호 (하이픈 없이 숫자만, 10~11자리)", example = "01012345678")
    private String phone;

    @Schema(description = "대학교", example = "한국대학교")
    private String university;

    @Schema(description = "본전공", example = "컴퓨터공학")
    private String major;

    @Schema(description = "부전공/복수전공 목록", example = "[\"통계학\"]")
    @JsonProperty("minor_double_major")
    private List<String> minorDoubleMajor;

    @Schema(description = "마지막 재학 학기", example = "4")
    @JsonProperty("last_semester")
    private Integer lastSemester;

    @Schema(description = "병역 상태", allowableValues = {"COMPLETED_OR_EXEMPT", "NOT_COMPLETED"})
    @JsonProperty("military_status")
    private MilitaryStatus militaryStatus;

    @Schema(description = "생년월일 (YYYY-MM-DD)", example = "2000-01-01")
    @JsonProperty("birth_date")
    private String birthDate;

    @Schema(description = "졸업 예정 시점", example = "2026-02")
    @JsonProperty("graduation_date")
    private String graduationDate;

    @Schema(description = "대학원 진학 여부", example = "false")
    @JsonProperty("grad_school_plan")
    private Boolean gradSchoolPlan;

    @Schema(description = "답변 목록. null이면 기존 answers 유지, 빈 배열이면 전체 삭제")
    @Valid
    private List<AnswerRequest> answers;
}
