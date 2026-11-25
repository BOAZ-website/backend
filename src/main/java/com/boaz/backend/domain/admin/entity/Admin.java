package com.boaz.backend.domain.admin.entity;

import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin")
public class Admin extends BaseEntity {

    public enum Role {
        @Schema(description = "서비스운영팀장") MASTER,
        @Schema(description = "슈퍼 관리자") SUPER,
        @Schema(description = "팀 관리자") TEAM,
        @Schema(description = "그룹(ADV/스터디) 리더") HOST
    }

    public enum TeamName {
        @Schema(description = "대표진") 대표진,
        @Schema(description = "차기대표진") 차기대표진,
        @Schema(description = "디자인팀") 디자인팀,
        @Schema(description = "자료연구팀") 자료연구팀,
        @Schema(description = "운영지원팀") 운영지원팀,
        @Schema(description = "기획팀") 기획팀,
        @Schema(description = "대외협력팀") 대외협력팀,
        @Schema(description = "서비스운영팀") 서비스운영팀,
        @Schema(description = "그룹리더(스터디장/ADV팀장)") 그룹리더,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Track track;

    @Column(nullable = false)
    private Integer term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamName teamName;

    private Long createdBy;

    private LocalDateTime deletedAt;

    /**
     * 계정 소유 동아리원({@code member.id}). DB에는 FK가 있다.
     *
     * <p>기존 계정을 백필하기 전까지 NULL인 계정이 있다. 값이 있다고 가정하지 않는다.
     * 회원당 삭제되지 않은 계정은 하나다(DB 생성 컬럼 {@code live_member_id}의 UNIQUE, 엔티티에는 매핑하지 않음).
     */
    private Long memberId;

    /** 운영진 팀장 여부. 표시와 점수 계산에만 쓰고 권한 계산에는 쓰지 않는다. HOST는 false. */
    @Column(nullable = false)
    private boolean isLeader;

    @Builder
    public Admin(String username, String password, Role role, String name,
                 Track track, Integer term, TeamName teamName, Long createdBy,
                 Long memberId, boolean isLeader) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.name = name;
        this.track = track;
        this.term = term;
        this.teamName = teamName;
        this.createdBy = createdBy;
        this.memberId = memberId;
        this.isLeader = isLeader;
    }

    public void update(Role role, String name, Track track, Integer term, TeamName teamName) {
        if (role != null) this.role = role;
        if (name != null) this.name = name;
        if (track != null) this.track = track;
        if (term != null) this.term = term;
        if (teamName != null) this.teamName = teamName;
    }

    public void resetPassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}