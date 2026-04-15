package com.boaz.backend.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class AdminPasswordResetRequest {

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$",
            message = "비밀번호는 8자 이상이며 영문, 숫자, 특수문자(!@#$%^&*)를 각 1개 이상 포함해야 합니다."
    )
    private String newPassword;
}
