package com.boaz.backend.domain.recruitment.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RecruitmentUpdateRequest {

    @Schema(description = "모집 기수", example = "28", nullable = true)
    private Integer term;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    @Schema(description = "모집 시작 일시", example = "2026-08-01T00:00:00", nullable = true)
    private LocalDateTime startDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    @Schema(description = "모집 종료 일시", example = "2026-08-15T23:59:59", nullable = true)
    private LocalDateTime endDate;

    @Schema(description = "모집 일정 목록 (수정 시 전체 교체)", nullable = true)
    private JsonNode schedule;

    @Schema(description = "홍보 책자 링크", example = "https://example.com/brochure.pdf", nullable = true)
    private String brochureUrl;
}
