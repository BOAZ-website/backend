package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.global.common.enums.Track;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Getter
@Builder
public class ApplicantSummaryResponse {

    private final Long id;

    private final Long userId;

    private final Applicant.ApplicantStatus status;

    private final Track track;

    private final String name;

    private final String email;

    private final String phone;

    private final String university;

    private final String major;

    private final List<String> minorDoubleMajor;

    private final Integer lastSemester;

    private final Applicant.MilitaryStatus militaryStatus;

    private final LocalDate birthDate;

    private final String graduationDate;

    private final Boolean gradSchoolPlan;

    private final LocalDateTime submittedAt;

    public static ApplicantSummaryResponse of(Applicant a, List<String> minorDoubleMajor) {
        return ApplicantSummaryResponse.builder()
                .id(a.getId())
                .userId(a.getUser().getId())
                .status(a.getStatus())
                .track(a.getTrack())
                .name(a.getName())
                .email(a.getEmail())
                .phone(a.getPhone())
                .university(a.getUniversity())
                .major(a.getMajor())
                .minorDoubleMajor(minorDoubleMajor)
                .lastSemester(a.getLastSemester())
                .militaryStatus(a.getMilitaryStatus())
                .birthDate(a.getBirthDate())
                .graduationDate(a.getGraduationDate())
                .gradSchoolPlan(a.getGradSchoolPlan())
                .submittedAt(a.getSubmittedAt())
                .build();
    }
}
