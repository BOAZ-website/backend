package com.boaz.backend.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "새 비밀번호 (8자 이상, 영문+숫자+특수문자 포함)", example = "NewBoaz1234!")
    private String newPassword;
}
