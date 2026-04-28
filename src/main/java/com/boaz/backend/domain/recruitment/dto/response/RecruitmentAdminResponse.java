package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RecruitmentAdminResponse {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Schema(description = "모집 공고 ID", example = "12")
    private final Long recruitmentId;

    @Schema(description = "모집 기수", example = "27")
    private final Integer term;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    @Schema(description = "모집 시작 일시", example = "2026-02-20T00:00:00")
    private final LocalDateTime startDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    @Schema(description = "모집 종료 일시", example = "2026-03-05T23:59:59")
    private final LocalDateTime endDate;

    @Schema(description = "모집 일정")
    private final JsonNode schedule;

    @Schema(description = "활성 여부", example = "true")
    private final Boolean isActive;

    @Schema(description = "홍보 책자 링크", example = "https://example.com/brochure.pdf", nullable = true)
    private final String brochureUrl;

    private RecruitmentAdminResponse(Recruitment recruitment) {
        this.recruitmentId = recruitment.getId();
        this.term = recruitment.getTerm();
        this.startDate = recruitment.getStartDate();
        this.endDate = recruitment.getEndDate();
        this.isActive = recruitment.isActive();
        this.brochureUrl = recruitment.getBrochureUrl();
        try {
            this.schedule = objectMapper.readTree(recruitment.getSchedule());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public static RecruitmentAdminResponse from(Recruitment recruitment) {
        return new RecruitmentAdminResponse(recruitment);
    }
}
