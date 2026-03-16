package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.recruitment.dto.SubscriptionRequest;
import com.boaz.backend.domain.recruitment.dto.SubscriptionResponse;
import com.boaz.backend.domain.recruitment.entity.Subscription;
import com.boaz.backend.domain.recruitment.repository.SubscriptionRepository;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public SubscriptionResponse subscribe(SubscriptionRequest request) {
        if (subscriptionRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        Subscription subscription = new Subscription(request.getEmail());
        return SubscriptionResponse.from(subscriptionRepository.save(subscription));
    }
}
