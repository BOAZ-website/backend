package com.boaz.backend.domain.admin.dto.request;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.openapitools.jackson.nullable.JsonNullable;

@Getter
public class AdminUpdateRequest {

    @Schema(description = "역할 (SUPER only, 본인 변경 불가)", example = "TEAM",
            allowableValues = {"SUPER", "TEAM"})
    private JsonNullable<Admin.Role> role = JsonNullable.undefined();

    @Schema(description = "이름", example = "김보아즈")
    private JsonNullable<String> name = JsonNullable.undefined();

    @Schema(description = "부문 (ALL 제외)", example = "ANALYSIS",
            allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private JsonNullable<Track> track = JsonNullable.undefined();

    @Schema(description = "기수", example = "25")
    private JsonNullable<Integer> term = JsonNullable.undefined();

    @Schema(description = "직책/소속", example = "서비스운영팀",
            allowableValues = {"대표진", "디자인팀", "자료연구팀", "운영지원팀", "기획팀", "대외협력팀", "서비스운영팀"})
    private JsonNullable<Admin.TeamName> teamName = JsonNullable.undefined();
}
