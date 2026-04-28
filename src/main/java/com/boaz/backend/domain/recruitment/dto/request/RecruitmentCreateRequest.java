package com.boaz.backend.domain.recruitment.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RecruitmentCreateRequest {

    @NotNull(message = "기수는 필수입니다.")
    @Schema(description = "모집 기수", example = "28")
    private Integer term;

    @NotNull(message = "모집 시작 일시는 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    @Schema(description = "모집 시작 일시", example = "2026-08-01T00:00:00")
    private LocalDateTime startDate;

    @NotNull(message = "모집 종료 일시는 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    @Schema(description = "모집 종료 일시", example = "2026-08-15T23:59:59")
    private LocalDateTime endDate;

    @NotNull(message = "모집 일정은 필수입니다.")
    @Schema(description = "모집 일정 목록")
    private JsonNode schedule;

    @Schema(description = "홍보 책자 링크", example = "https://example.com/brochure.pdf", nullable = true)
    private String brochureUrl;
}
