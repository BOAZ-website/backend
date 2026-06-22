package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.global.common.enums.Track;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Getter
@Builder
public class ApplicantEvaluationResponse {

    private final Long id;

    private final Long userId;

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

    // 평가자별 applicant_eval 집계 (서버 계산)
    private final int passCount;

    private final int holdCount;

    private final int failCount;

    private final int totalScore;

    private final EvaluationDecision finalDecision;

    public static ApplicantEvaluationResponse of(Applicant a, List<String> minorDoubleMajor,
                                                 int passCount, int holdCount, int failCount, int totalScore) {
        return ApplicantEvaluationResponse.builder()
                .id(a.getId())
                .userId(a.getUser().getId())
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
                .passCount(passCount)
                .holdCount(holdCount)
                .failCount(failCount)
                .totalScore(totalScore)
                .finalDecision(a.getFinalDecision())
                .build();
    }
}
