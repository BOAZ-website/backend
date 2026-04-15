package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecruitmentResponse {

    @Schema(description = "기수", example = "25", nullable = true)
    private final Integer term;

    @Schema(description = "모집 시작 일시", example = "2024-03-01T00:00:00", nullable = true)
    private final LocalDateTime startDate;

    @Schema(description = "모집 마감 일시", example = "2024-03-31T23:59:59", nullable = true)
    private final LocalDateTime endDate;

    @Schema(description = "일정 정보", nullable = true)
    private final JsonNode schedule;

    @Schema(description = "모집 공고 URL", example = "https://example.com/brochure", nullable = true)
    private final String brochureUrl;

    @Schema(description = "모집 중 여부", example = "true")
    private final Boolean isActive;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private RecruitmentResponse(Recruitment recruitment, boolean isActive) {
        this.term = recruitment.getTerm();
        this.startDate = recruitment.getStartDate();
        this.endDate = recruitment.getEndDate();
        try {
                this.schedule = objectMapper.readTree(recruitment.getSchedule());
            } catch (Exception e) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        this.brochureUrl = recruitment.getBrochureUrl();
        this.isActive = isActive;
    }

    private RecruitmentResponse(boolean isActive) {
        this.term = null;
        this.startDate = null;
        this.endDate = null;
        this.schedule = null;
        this.brochureUrl = null;
        this.isActive = isActive;
    }

    public static RecruitmentResponse from(Recruitment recruitment) {
        return new RecruitmentResponse(recruitment, true);
    }

    public static RecruitmentResponse inactive() {
        return new RecruitmentResponse(false);
    }
}