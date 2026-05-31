package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.Recruitment;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class RecruitmentRepositoryTest extends TestcontainersBase {

    @Autowired RecruitmentRepository recruitmentRepository;
    @Autowired TestEntityManager em;

    private Recruitment persistRecruitment(int term, LocalDateTime start, LocalDateTime end) {
        Recruitment r = Recruitment.create(term, start, end, "{}", null);
        return em.persistFlushFind(r);
    }

    // ──────────────────────────────────────────────
    // findActiveRecruitment: start_date <= now <= end_date
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("findActiveRecruitment")
    class FindActiveRecruitment {

        @Test
        @DisplayName("now 가 모집 기간 내면 해당 공고 반환")
        void within() {
            LocalDateTime now = LocalDateTime.now();
            persistRecruitment(27, now.minusDays(1), now.plusDays(1));

            Optional<Recruitment> result = recruitmentRepository.findActiveRecruitment(now);

            assertThat(result).isPresent();
            assertThat(result.get().getTerm()).isEqualTo(27);
        }

        @Test
        @DisplayName("now 가 모집 종료 이후면 empty")
        void afterEnd() {
            LocalDateTime now = LocalDateTime.now();
            persistRecruitment(27, now.minusDays(10), now.minusDays(1));

            assertThat(recruitmentRepository.findActiveRecruitment(now)).isEmpty();
        }

        @Test
        @DisplayName("경계: start_date 정각도 모집 중 포함")
        void startBoundaryInclusive() {
            LocalDateTime now = LocalDateTime.now();
            persistRecruitment(27, now, now.plusDays(7));

            assertThat(recruitmentRepository.findActiveRecruitment(now)).isPresent();
        }

        @Test
        @DisplayName("경계: end_date 정각도 모집 중 포함")
        void endBoundaryInclusive() {
            LocalDateTime now = LocalDateTime.now();
            persistRecruitment(27, now.minusDays(7), now);

            assertThat(recruitmentRepository.findActiveRecruitment(now)).isPresent();
        }
    }

    @Nested
    @DisplayName("findByTerm / existsByTerm")
    class ByTerm {

        @Test
        @DisplayName("기수로 공고 조회")
        void findByTerm() {
            LocalDateTime now = LocalDateTime.now();
            persistRecruitment(26, now.minusDays(1), now.plusDays(1));

            assertThat(recruitmentRepository.findByTerm(26)).isPresent();
            assertThat(recruitmentRepository.findByTerm(99)).isEmpty();
        }

        @Test
        @DisplayName("existsByTerm: 존재/미존재")
        void existsByTerm() {
            LocalDateTime now = LocalDateTime.now();
            persistRecruitment(26, now.minusDays(1), now.plusDays(1));

            assertThat(recruitmentRepository.existsByTerm(26)).isTrue();
            assertThat(recruitmentRepository.existsByTerm(27)).isFalse();
        }

        @Test
        @DisplayName("existsByTermAndIdNot: 자기 자신은 중복으로 보지 않음")
        void existsByTermAndIdNot() {
            LocalDateTime now = LocalDateTime.now();
            Recruitment r = persistRecruitment(26, now.minusDays(1), now.plusDays(1));

            // 같은 term 이지만 자기 자신(id) 제외 → 중복 아님
            assertThat(recruitmentRepository.existsByTermAndIdNot(26, r.getId())).isFalse();
            // 다른 id 기준으로 보면 중복
            assertThat(recruitmentRepository.existsByTermAndIdNot(26, r.getId() + 1)).isTrue();
        }
    }

    @Test
    @DisplayName("findAllByOrderByTermDesc: term 내림차순 정렬")
    void findAllByOrderByTermDesc() {
        LocalDateTime now = LocalDateTime.now();
        persistRecruitment(25, now.minusDays(1), now.plusDays(1));
        persistRecruitment(27, now.minusDays(1), now.plusDays(1));
        persistRecruitment(26, now.minusDays(1), now.plusDays(1));

        List<Recruitment> result = recruitmentRepository.findAllByOrderByTermDesc();

        assertThat(result).extracting(Recruitment::getTerm).containsExactly(27, 26, 25);
    }
}
