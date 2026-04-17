package com.boaz.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequest {
    
    @NotBlank(message = "요청 파라미터가 누락되었습니다.")
    private String username;

    @NotBlank (message = "요청 파라미터가 누락되었습니다.")
    private String password;
}
