package com.boaz.backend.domain.user.entity;

import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private String phone;
    private String university;
    private String major;

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
}
