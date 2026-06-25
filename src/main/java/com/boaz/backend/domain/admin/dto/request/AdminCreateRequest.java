package com.boaz.backend.domain.admin.dto.request;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class AdminCreateRequest {

    @NotBlank
    @Schema(description = "로그인 ID", example = "boaz_team2")
    private String username;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$",
            message = "비밀번호는 최소 8자 이상, 영문+숫자+특수문자(!@#$%^&*)를 포함해야 합니다."
    )
    @Schema(description = "비밀번호 (8자 이상, 영문+숫자+특수문자 포함)", example = "Boaz1234!")
    private String password;

    @NotNull
    @Schema(description = "역할", example = "TEAM", allowableValues = {"SUPER", "TEAM"})
    private Admin.Role role;

    @NotBlank
    @Schema(description = "이름", example = "김보아즈")
    private String name;

    @NotNull
    @Schema(description = "부문 (ALL 제외)", example = "ANALYSIS",
            allowableValues = {"ANALYSIS", "VISUALIZATION", "ENGINEERING"})
    private Track track;

    @NotNull
    @Min(0)
    @Schema(description = "기수", example = "25")
    private Integer term;

    @NotNull
    @Schema(description = "직책/소속", example = "서비스운영팀",
            allowableValues = {"대표진", "차기대표진", "디자인팀", "자료연구팀", "운영지원팀", "기획팀", "대외협력팀", "서비스운영팀"})
    private Admin.TeamName teamName;
}
