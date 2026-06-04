package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion.Category;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion.Type;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.global.config.JpaConfig;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class ApplicationQuestionRepositoryTest extends TestcontainersBase {

    @Autowired ApplicationQuestionRepository questionRepository;
    @Autowired TestEntityManager em;

    private Recruitment persistRecruitment(int term) {
        LocalDateTime now = LocalDateTime.now();
        return em.persistFlushFind(Recruitment.create(term, now.minusDays(1), now.plusDays(1), "{}", null));
    }

    private void persistQuestion(Recruitment r, String label, Category category, int orderNum) {
        ApplicationQuestion q = ApplicationQuestion.create(
                r, label, category, Type.TEXT, "content", null, 500, null, orderNum, true);
        em.persist(q);
    }

    @Test
    @DisplayName("findByRecruitmentIdAndCategories: COMMON + 지정 트랙만, order_num 오름차순")
    void findByCategories() {
        Recruitment r = persistRecruitment(27);
        persistQuestion(r, "공통2", Category.COMMON, 2);
        persistQuestion(r, "공통1", Category.COMMON, 1);
        persistQuestion(r, "엔지니어링", Category.ENGINEERING, 3);
        persistQuestion(r, "시각화", Category.VISUALIZATION, 1);
        em.flush();
        em.clear();

        List<ApplicationQuestion> result = questionRepository.findByRecruitmentIdAndCategories(
                r.getId(), Category.COMMON, Category.ENGINEERING);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ApplicationQuestion::getOrderNum).containsExactly(1, 2, 3);
        assertThat(result).noneMatch(q -> q.getCategory() == Category.VISUALIZATION);
    }

    @Test
    @DisplayName("findByRecruitmentIdOrderByOrderNumAsc: order_num 오름차순 전체")
    void findOrdered() {
        Recruitment r = persistRecruitment(27);
        persistQuestion(r, "q3", Category.COMMON, 3);
        persistQuestion(r, "q1", Category.COMMON, 1);
        persistQuestion(r, "q2", Category.ENGINEERING, 2);
        em.flush();
        em.clear();

        List<ApplicationQuestion> result = questionRepository.findByRecruitmentIdOrderByOrderNumAsc(r.getId());

        assertThat(result).extracting(ApplicationQuestion::getOrderNum).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("중복 검증 쿼리: label / (category, order_num) / 자기 자신 제외")
    void existsQueries() {
        Recruitment r = persistRecruitment(27);
        persistQuestion(r, "공통1", Category.COMMON, 1);
        em.flush();
        Long savedId = questionRepository.findByRecruitmentIdOrderByOrderNumAsc(r.getId()).get(0).getId();
        em.clear();

        assertThat(questionRepository.existsByRecruitmentIdAndLabel(r.getId(), "공통1")).isTrue();
        assertThat(questionRepository.existsByRecruitmentIdAndLabel(r.getId(), "없는라벨")).isFalse();

        assertThat(questionRepository.existsByRecruitmentIdAndCategoryAndOrderNum(
                r.getId(), Category.COMMON, 1)).isTrue();
        // 자기 자신(savedId) 제외 시 중복 아님
        assertThat(questionRepository.existsByRecruitmentIdAndCategoryAndOrderNumAndIdNot(
                r.getId(), Category.COMMON, 1, savedId)).isFalse();
        assertThat(questionRepository.existsByRecruitmentIdAndCategoryAndOrderNumAndIdNot(
                r.getId(), Category.COMMON, 1, savedId + 1)).isTrue();
    }
}
