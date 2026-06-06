package com.boaz.backend.domain.recruitment.dto.request;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.global.common.enums.Track;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;


@Getter
public class ApplicationRequest {

    @Schema(description = "지원 부문", example = "ENGINEERING", allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    @NotNull(message = "지원 부문을 입력해주세요.")
    private Track track;

    @Schema(description = "성명", example = "홍길동")
    @NotBlank(message = "성명을 입력해주세요.")
    private String name;

    @Schema(description = "이메일", example = "hong@example.com")
    @NotBlank(message = "이메일을 입력해주세요.")
    private String email;

    @Schema(description = "전화번호", example = "01012345678")
    @NotBlank(message = "전화번호를 입력해주세요.")
    private String phone;

    @Schema(description = "대학교", example = "한국대학교")
    @NotBlank(message = "대학교를 입력해주세요.")
    private String university;

    @Schema(description = "본전공", example = "컴퓨터공학")
    @NotBlank(message = "본전공을 입력해주세요.")
    private String major;

    @Schema(description = "부전공/복수전공 목록", example = "[\"통계학\"]")
    @JsonProperty("minor_double_major")
    private List<String> minorDoubleMajor;

    @Schema(description = "마지막 재학 학기", example = "4")
    @NotNull(message = "마지막 재학 학기를 입력해주세요.")
    @JsonProperty("last_semester")
    private Integer lastSemester;

    @Schema(description = "병역 상태", example = "COMPLETED_OR_EXEMPT", allowableValues = {"COMPLETED_OR_EXEMPT", "NOT_COMPLETED"})
    @NotNull(message = "병역 상태를 선택해주세요.")
    @JsonProperty("military_status")
    private Applicant.MilitaryStatus militaryStatus;

    @Schema(description = "생년월일", example = "2000-01-01")
    @NotBlank(message = "생년월일을 입력해주세요.")
    @JsonProperty("birth_date")
    private String birthDate;

    @Schema(description = "졸업 예정 시점", example = "2025-02")
    @NotBlank(message = "졸업 예정 시점을 입력해주세요.")
    @JsonProperty("graduation_date")
    private String graduationDate;

    @Schema(description = "대학원 진학 여부", example = "false")
    @NotNull(message = "대학원 진학 여부를 선택해주세요.")
    @JsonProperty("grad_school_plan")
    private Boolean gradSchoolPlan;

    @Schema(description = "지원서 답변 목록")
    @NotNull(message = "답변 목록을 입력해주세요.")
    @Valid
    private List<AnswerRequest> answers;
}