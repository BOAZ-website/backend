package com.boaz.backend.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9..")
    private String accessToken;

    @JsonIgnore
    private String refreshToken;
    
}
