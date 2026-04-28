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

    @Schema(description = "모집 공고 ID", example = "12", nullable = true)
    private final Long recruitmentId;

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
        this.recruitmentId = recruitment.getId();
        this.term = isActive ? recruitment.getTerm() : null;
        this.startDate = isActive ? recruitment.getStartDate() : null;
        this.endDate = isActive ? recruitment.getEndDate() : null;
        this.brochureUrl = isActive ? recruitment.getBrochureUrl() : null;
        this.isActive = isActive;
        if (isActive) {
            try {
                this.schedule = objectMapper.readTree(recruitment.getSchedule());
            } catch (Exception e) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        } else {
            this.schedule = null;
        }
    }

    public static RecruitmentResponse from(Recruitment recruitment, boolean isActive) {
        return new RecruitmentResponse(recruitment, isActive);
    }
}
