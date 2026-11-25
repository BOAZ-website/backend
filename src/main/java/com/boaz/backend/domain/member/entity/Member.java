package com.boaz.backend.domain.member.entity;

import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 동아리원 명단. 웹사이트 로그인 계정(users)·관리자 계정(admin)과 별개로 관리한다.
 *
 * <p>탈퇴·제명은 {@link #status}로 기록하고, {@link #deletedAt}은 오등록 정정에만 쓴다.
 * track은 {@link Track#ALL}을 허용하지 않는다(DB CHECK {@code ck_member_track}).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    public enum Status {
        @Schema(description = "활동 중") ACTIVE,
        @Schema(description = "수료") COMPLETED,
        @Schema(description = "미수료") NOT_COMPLETED,
        @Schema(description = "탈퇴") WITHDRAWN,
        @Schema(description = "제명") EXPELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Track track;

    @Column(nullable = false)
    private String name;

    @Column(length = 2)
    private String nameSuffix;

    @Column(length = 20)
    private String phone;

    private String email;

    private LocalDate birthDate;

    @Column(length = 100)
    private String university;

    @Column(length = 100)
    private String major;

    private Long sourceUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Version
    @Column(nullable = false)
    private Long version;

    private LocalDateTime deletedAt;

    @Builder
    public Member(Integer term, Track track, String name, String nameSuffix, String phone, String email,
                  LocalDate birthDate, String university, String major, Long sourceUserId, Status status) {
        this.term = term;
        this.track = track;
        this.name = name;
        this.nameSuffix = nameSuffix;
        this.phone = phone;
        this.email = email;
        this.birthDate = birthDate;
        this.university = university;
        this.major = major;
        this.sourceUserId = sourceUserId;
        this.status = status;
    }
}
