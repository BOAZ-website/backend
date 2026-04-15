package com.boaz.backend.domain.recruitment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.Track;

import java.time.LocalDate;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "applicants")
@EntityListeners(AuditingEntityListener.class)
public class Applicant extends BaseEntity  {
    
    // 필_또는_면제, 미필
    public enum MilitaryStatus {
        COMPLETED_OR_EXEMPT, NOT_COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_id", nullable = false)
    private Recruitment recruitment;

    @Column(length = 50)
    @Enumerated(EnumType.STRING)
    private Track track;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 255, nullable = false)
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

    @Builder
    public Applicant(Recruitment recruitment, Track track, String name, String email,
                    String phone, String university, String major, String minorDoubleMajor,
                    Integer lastSemester, MilitaryStatus militaryStatus, LocalDate birthDate,
                    String graduationDate, Boolean gradSchoolPlan) {
        this.recruitment = recruitment;
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
}