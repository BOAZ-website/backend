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
        @Schema(description = "슈퍼 관리자") SUPER,
        @Schema(description = "팀 관리자") TEAM
    }

    public enum TeamName {
        @Schema(description = "대표진") 대표진,
        @Schema(description = "디자인팀") 디자인팀,
        @Schema(description = "자료연구팀") 자료연구팀,
        @Schema(description = "운영지원팀") 운영지원팀,
        @Schema(description = "기획팀") 기획팀,
        @Schema(description = "대외협력팀") 대외협력팀,
        @Schema(description = "서비스운영팀") 서비스운영팀,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
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

    @Builder
    public Admin(String username, String password, Role role, String name,
                 Track track, Integer term, TeamName teamName, Long createdBy) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.name = name;
        this.track = track;
        this.term = term;
        this.teamName = teamName;
        this.createdBy = createdBy;
    }

    public void updateRole(Role role) {
        this.role = role;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateTrack(Track track) {
        this.track = track;
    }

    public void updateTerm(Integer term) {
        this.term = term;
    }

    public void updateTeamName(TeamName teamName) {
        this.teamName = teamName;
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