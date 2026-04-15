package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class DeadlineResponse {

    @JsonProperty("recruitment_id")
    private final Long recruitmentId;

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