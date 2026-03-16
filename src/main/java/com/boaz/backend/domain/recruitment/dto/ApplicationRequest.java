package com.boaz.backend.domain.recruitment.dto;

import com.boaz.backend.domain.recruitment.entity.MilitaryStatus;
import com.boaz.backend.global.common.enums.Track;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class ApplicationRequest {

    @NotNull(message = "recruitment_id를 입력해주세요.")
    @JsonProperty("recruitment_id")
    private Long recruitmentId;

    @NotNull(message = "지원 부문을 입력해주세요.")
    private Track track;

    @NotBlank(message = "성명을 입력해주세요.")
    private String name;

    @NotBlank(message = "이메일을 입력해주세요.")
    private String email;

    @NotBlank(message = "전화번호를 입력해주세요.")
    private String phone;

    @NotBlank(message = "대학교를 입력해주세요.")
    private String university;

    @NotBlank(message = "본전공을 입력해주세요.")
    private String major;

    @JsonProperty("minor_double_major")
    private List<String> minorDoubleMajor;

    @NotNull(message = "마지막 재학 학기를 입력해주세요.")
    @JsonProperty("last_semester")
    private Integer lastSemester;

    @NotNull(message = "병역 상태를 선택해주세요.")
    @JsonProperty("military_status")
    private MilitaryStatus militaryStatus;

    @NotBlank(message = "생년월일을 입력해주세요.")
    @JsonProperty("birth_date")
    private String birthDate;

    @NotBlank(message = "졸업 예정 시점을 입력해주세요.")
    @JsonProperty("graduation_date")
    private String graduationDate;

    @NotNull(message = "대학원 진학 여부를 선택해주세요.")
    @JsonProperty("grad_school_plan")
    private Boolean gradSchoolPlan;

    @NotNull(message = "답변 목록을 입력해주세요.")
    @Valid
    private List<AnswerRequest> answers;
}