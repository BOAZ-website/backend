package com.boaz.backend.domain.recruitment.dto.response;

import com.boaz.backend.domain.recruitment.entity.Subscription;
import lombok.Getter;

@Getter
public class SubscriptionResponse {

    private final String email;

    private SubscriptionResponse(Subscription subscription) {
        this.email = subscription.getEmail();
    }

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(subscription);
    }
}