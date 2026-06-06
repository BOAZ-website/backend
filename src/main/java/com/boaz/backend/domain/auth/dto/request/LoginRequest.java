package com.boaz.backend.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequest {
    
    @Schema(description = "로그인 ID", example = "boaz_super")
    @NotBlank(message = "username이 누락되었습니다.")
    private String username;

    @Schema(description = "비밀번호", example = "Boaz1234!")
    @NotBlank (message = "password가 누락되었습니다.")
    private String password;
}
