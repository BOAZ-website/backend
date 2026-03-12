package com.boaz.backend.domain.recruitment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.Track;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "applicants")
@EntityListeners(AuditingEntityListener.class)
public class Applicant extends BaseEntity  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_id", nullable = false)
    private Recruitment recruitment;

    @Column(length = 50)
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
}