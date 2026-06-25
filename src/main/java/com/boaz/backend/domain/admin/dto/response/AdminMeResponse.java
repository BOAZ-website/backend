package com.boaz.backend.domain.admin.dto.response;

import com.boaz.backend.domain.admin.entity.Admin;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminMeResponse {

    @Schema(description = "계정 ID", example = "1")
    private Long id;

    @Schema(description = "직책/소속", example = "대표진",
            allowableValues = {"대표진", "디자인팀", "자료연구팀", "운영지원팀", "기획팀", "대외협력팀", "서비스운영팀"})
    private String teamName;

    @Schema(description = "이름", example = "문혁준")
    private String name;

    public static AdminMeResponse from(Admin admin) {
        return AdminMeResponse.builder()
                .id(admin.getId())
                .teamName(admin.getTeamName().name())
                .name(admin.getName())
                .build();
    }
}
