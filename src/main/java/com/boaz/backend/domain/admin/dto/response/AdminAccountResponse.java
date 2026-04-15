package com.boaz.backend.domain.admin.dto.response;

import com.boaz.backend.domain.admin.entity.Admin;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminAccountResponse {

    private Long id;
    private String username;
    private String role;
    private String name;
    private String track;
    private Integer term;
    private String teamName;
    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
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
