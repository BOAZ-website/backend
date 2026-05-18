package com.boaz.backend.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserTokenRefreshResponse(
        @JsonProperty("access_token") String accessToken
) {}
