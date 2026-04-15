package com.boaz.backend.domain.recruitment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SubscriptionRequest {

    @Schema(description = "이메일", example = "hong@example.com")
    @NotBlank(message = "이메일을 입력해주세요.")
    private String email;
}