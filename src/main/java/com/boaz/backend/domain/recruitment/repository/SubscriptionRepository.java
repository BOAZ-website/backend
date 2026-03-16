package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByEmail(String email);
}
