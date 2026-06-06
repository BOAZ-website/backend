package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Subscription;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SubscriptionResponse {

    @Schema(description = "구독 ID", example = "1")
    private final Long id;

    @Schema(description = "구독 이메일", example = "hong@example.com")
    private final String email;

    @Schema(description = "신청 일시")
    private final LocalDateTime createdAt;

    private SubscriptionResponse(Subscription subscription) {
        this.id = subscription.getId();
        this.email = subscription.getEmail();
        this.createdAt = subscription.getCreatedAt();
    }

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(subscription);
    }
}