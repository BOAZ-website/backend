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

    @NotNull
    @JsonProperty("recruitment_id")
    private Long recruitmentId;

    @NotNull
    private Track track;

    @NotBlank
    private String name;

    @NotBlank
    private String email;

    @NotBlank
    private String phone;

    @NotBlank
    private String university;

    @NotBlank
    private String major;

    @JsonProperty("minor_double_major")
    private List<String> minorDoubleMajor;

    @NotNull
    @JsonProperty("last_semester")
    private Integer lastSemester;

    @NotNull
    @JsonProperty("military_status")
    private MilitaryStatus militaryStatus;

    @NotBlank
    @JsonProperty("birth_date")
    private String birthDate;

    @NotBlank
    @JsonProperty("graduation_date")
    private String graduationDate;

    @NotNull
    @JsonProperty("grad_school_plan")
    private Boolean gradSchoolPlan;

    @NotNull
    @Valid
    private List<AnswerRequest> answers;
}