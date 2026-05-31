package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.Subscription;
import com.boaz.backend.global.config.JpaConfig;
import com.boaz.backend.support.TestcontainersBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class SubscriptionRepositoryTest extends TestcontainersBase {

    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired TestEntityManager em;

    @Test
    @DisplayName("existsByEmail / unique 제약 위반")
    void existsAndUnique() {
        subscriptionRepository.saveAndFlush(Subscription.builder().email("a@example.com").build());

        assertThat(subscriptionRepository.existsByEmail("a@example.com")).isTrue();
        assertThat(subscriptionRepository.existsByEmail("none@example.com")).isFalse();

        // 동일 email 중복 저장 → unique 제약 위반
        assertThatThrownBy(() ->
                subscriptionRepository.saveAndFlush(Subscription.builder().email("a@example.com").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findAllByOrderByCreatedAtDesc: created_at 내림차순(최신순)")
    void orderByCreatedAtDesc() {
        Subscription s1 = subscriptionRepository.saveAndFlush(Subscription.builder().email("old@example.com").build());
        Subscription s2 = subscriptionRepository.saveAndFlush(Subscription.builder().email("mid@example.com").build());
        Subscription s3 = subscriptionRepository.saveAndFlush(Subscription.builder().email("new@example.com").build());

        // @CreatedDate 는 동일 트랜잭션에서 같은 값이 될 수 있으므로 created_at 을 네이티브로 명시 세팅
        EntityManager entityManager = em.getEntityManager();
        setCreatedAt(entityManager, s1.getId(), "2026-03-01 10:00:00");
        setCreatedAt(entityManager, s2.getId(), "2026-03-10 10:00:00");
        setCreatedAt(entityManager, s3.getId(), "2026-03-15 10:00:00");
        em.clear();

        List<Subscription> result = subscriptionRepository.findAllByOrderByCreatedAtDesc();

        assertThat(result).extracting(Subscription::getEmail)
                .containsExactly("new@example.com", "mid@example.com", "old@example.com");
    }

    @Test
    @DisplayName("deleteAll: 전체 삭제")
    void deleteAll() {
        subscriptionRepository.saveAndFlush(Subscription.builder().email("a@example.com").build());
        subscriptionRepository.saveAndFlush(Subscription.builder().email("b@example.com").build());

        subscriptionRepository.deleteAll();
        em.flush();

        assertThat(subscriptionRepository.count()).isZero();
    }

    private void setCreatedAt(EntityManager entityManager, Long id, String createdAt) {
        entityManager.createNativeQuery("UPDATE subscription SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, id)
                .executeUpdate();
    }
}
