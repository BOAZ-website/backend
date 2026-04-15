package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class DeadlineResponse {

    @Schema(description = "모집 ID", example = "1")
    @JsonProperty("recruitment_id")
    private final Long recruitmentId;

    @Schema(description = "마감 일시", example = "2024-03-31T23:59:59")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime deadline;

    private DeadlineResponse(Recruitment recruitment) {
        this.recruitmentId = recruitment.getId();
        this.deadline = recruitment.getEndDate();
    }

    public static DeadlineResponse from(Recruitment recruitment) {
        return new DeadlineResponse(recruitment);
    }
}