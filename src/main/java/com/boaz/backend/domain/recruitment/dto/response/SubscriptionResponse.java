package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Subscription;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class SubscriptionResponse {

    @Schema(description = "구독 이메일", example = "hong@example.com")
    private final String email;

    private SubscriptionResponse(Subscription subscription) {
        this.email = subscription.getEmail();
    }

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(subscription);
    }
}