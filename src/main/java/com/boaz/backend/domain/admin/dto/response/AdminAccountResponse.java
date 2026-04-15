package com.boaz.backend.domain.admin.dto.response;

import com.boaz.backend.domain.admin.entity.Admin;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminAccountResponse {

    @Schema(description = "계정 ID", example = "1")
    private Long id;

    @Schema(description = "로그인 ID", example = "boaz_team2")
    private String username;

    @Schema(description = "역할", example = "TEAM", allowableValues = {"SUPER", "TEAM"})
    private String role;

    @Schema(description = "이름", example = "김보아즈")
    private String name;

    @Schema(description = "부문", example = "ANALYSIS",
            allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private String track;

    @Schema(description = "기수", example = "25")
    private Integer term;

    @Schema(description = "직책/소속", example = "서비스운영팀",
            allowableValues = {"대표진", "디자인팀", "자료연구팀", "운영지원팀", "기획팀", "대외협력팀", "서비스운영팀"})
    private String teamName;

    @Schema(description = "생성한 SUPER 계정 ID", example = "1", nullable = true)
    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    @Schema(description = "생성 일시", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    @Schema(description = "수정 일시", example = "2025-01-01T00:00:00")
    private LocalDateTime updatedAt;

    public static AdminAccountResponse from(Admin admin) {
        return AdminAccountResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .role(admin.getRole().name())
                .name(admin.getName())
                .track(admin.getTrack().name())
                .term(admin.getTerm())
                .teamName(admin.getTeamName().name())
                .createdBy(admin.getCreatedBy())
                .createdAt(admin.getCreatedAt())
                .updatedAt(admin.getUpdatedAt())
                .build();
    }
}
