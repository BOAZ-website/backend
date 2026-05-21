package com.boaz.backend.domain.recruitment.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.Track;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "applicants")
@EntityListeners(AuditingEntityListener.class)
public class Applicant extends BaseEntity {

    public enum MilitaryStatus {
        @Schema(description = "필 또는 면제")
        COMPLETED_OR_EXEMPT,
        @Schema(description = "미필")
        NOT_COMPLETED
    }

    public enum ApplicantStatus {
        DRAFT,
        SUBMITTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_id", nullable = false)
    private Recruitment recruitment;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicantStatus status;

    @Column(length = 50)
    @Enumerated(EnumType.STRING)
    private Track track;

    @Column(length = 100)
    private String name;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String university;

    @Column(length = 100)
    private String major;

    @Column(columnDefinition = "JSON")
    private String minorDoubleMajor;

    private Integer lastSemester;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private MilitaryStatus militaryStatus;

    private LocalDate birthDate;

    @Column(length = 7)
    private String graduationDate;

    private Boolean gradSchoolPlan;

    private LocalDateTime submittedAt;

    @Builder
    public Applicant(Recruitment recruitment, User user, ApplicantStatus status, Track track,
                     String name, String email, String phone, String university, String major,
                     String minorDoubleMajor, Integer lastSemester, MilitaryStatus militaryStatus,
                     LocalDate birthDate, String graduationDate, Boolean gradSchoolPlan) {
        this.recruitment = recruitment;
        this.user = user;
        this.status = status != null ? status : ApplicantStatus.DRAFT;
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
    }

    // 임시저장 부분 업데이트 (null이면 기존값 유지)
    public void update(Track track, String name, String email, String phone,
                       String university, String major, String minorDoubleMajor,
                       Integer lastSemester, MilitaryStatus militaryStatus,
                       LocalDate birthDate, String graduationDate, Boolean gradSchoolPlan) {
        if (track != null) this.track = track;
        if (name != null) this.name = name;
        if (email != null) this.email = email;
        if (phone != null) this.phone = phone;
        if (university != null) this.university = university;
        if (major != null) this.major = major;
        if (minorDoubleMajor != null) this.minorDoubleMajor = minorDoubleMajor;
        if (lastSemester != null) this.lastSemester = lastSemester;
        if (militaryStatus != null) this.militaryStatus = militaryStatus;
        if (birthDate != null) this.birthDate = birthDate;
        if (graduationDate != null) this.graduationDate = graduationDate;
        if (gradSchoolPlan != null) this.gradSchoolPlan = gradSchoolPlan;
    }

    // 신규 제출 시 submittedAt 설정 (DRAFT 없이 바로 제출하는 경우)
    public void markSubmitted() {
        this.status = ApplicantStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
    }

    // 제출 시 전체 덮어쓰기 + SUBMITTED 전환
    public void overwrite(Track track, String name, String email, String phone,
                          String university, String major, String minorDoubleMajor,
                          Integer lastSemester, MilitaryStatus militaryStatus,
                          LocalDate birthDate, String graduationDate, Boolean gradSchoolPlan) {
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
        this.status = ApplicantStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
    }
}
