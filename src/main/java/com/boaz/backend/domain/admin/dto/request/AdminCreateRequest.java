package com.boaz.backend.domain.admin.dto.request;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class AdminCreateRequest {

    @NotBlank
    private String username;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$",
            message = "비밀번호는 최소 8자 이상, 영문+숫자+특수문자(!@#$%^&*)를 포함해야 합니다."
    )
    private String password;

    @NotNull
    private Admin.Role role;

    @NotBlank
    private String name;

    @NotNull
    private Track track;

    @NotNull
    private Integer term;

    @NotNull
    private Admin.TeamName teamName;
}
