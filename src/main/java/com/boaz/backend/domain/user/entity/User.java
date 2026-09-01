package com.boaz.backend.domain.user.entity;

import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.MilitaryStatus;
import com.boaz.backend.global.common.enums.Track;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider", "provider_id"})
})
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(length = 100)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberType memberType;

    // 승격 시 Applicant에서 복사되는 필드 (기본 null)
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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Track track;

    private Integer term;

    @Builder
    public User(String provider, String providerId, String nickname, MemberType memberType) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.memberType = memberType;
    }

    // 합격자 승격: memberType → MEMBER, 지원서 개인정보 복사
    public void promote(String name, String email, String phone, String university, String major,
                        String minorDoubleMajor, Integer lastSemester, MilitaryStatus militaryStatus,
                        LocalDate birthDate, String graduationDate, Boolean gradSchoolPlan,
                        Track track, Integer term) {
        this.memberType = MemberType.MEMBER;
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
        this.track = track;
        this.term = term;
    }
}
