package com.boaz.backend.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AdminIdResponse {

    @Schema(description = "계정 ID", example = "1")
    private final Long id;
}
