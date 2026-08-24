package com.boaz.backend.domain.faq.repository;

import com.boaz.backend.domain.faq.entity.Faq;
import com.boaz.backend.global.config.JpaConfig;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class FaqRepositoryTest extends TestcontainersBase {

    @Autowired FaqRepository faqRepository;
    @Autowired TestEntityManager em;

    private Faq persistFaq(Faq.Category category, int orderNum) {
        return em.persistFlushFind(Faq.create("질문 " + orderNum, "답변 " + orderNum, category, orderNum));
    }

    @Nested
    @DisplayName("(category, order_num) 유니크 제약")
    class CategoryOrderNumUniqueConstraint {

        @Test
        @DisplayName("동일 category+orderNum 두 번째 저장 시 DataIntegrityViolationException 발생")
        void duplicateCategoryOrderNumViolatesConstraint() {
            persistFaq(Faq.Category.RECRUITMENT, 1);

            assertThatThrownBy(() -> {
                faqRepository.save(Faq.create("다른 질문", "다른 답변", Faq.Category.RECRUITMENT, 1));
                faqRepository.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("동일 orderNum 이라도 category 가 다르면 정상 저장")
        void sameOrderNumDifferentCategoryAllowed() {
            persistFaq(Faq.Category.RECRUITMENT, 1);
            persistFaq(Faq.Category.ACTIVITY, 1);

            assertThat(faqRepository.count()).isEqualTo(2);
        }
    }
}
