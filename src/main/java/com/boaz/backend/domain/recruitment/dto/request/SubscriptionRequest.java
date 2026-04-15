package com.boaz.backend.domain.recruitment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SubscriptionRequest {

    @NotBlank(message = "이메일을 입력해주세요.")
    private String email;
}