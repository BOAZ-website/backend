package com.boaz.backend.domain.curriculum.repository;

import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.boaz.backend.global.common.enums.Track;
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
class CurriculumRepositoryTest extends TestcontainersBase {

    @Autowired CurriculumRepository curriculumRepository;
    @Autowired TestEntityManager em;

    private Curriculum persistCurriculum(Track track) {
        return em.persistFlushFind(Curriculum.create(track, "[{\"step\":1,\"title\":\"제목\",\"desc\":\"설명\"}]"));
    }

    @Nested
    @DisplayName("track 유니크 제약")
    class TrackUniqueConstraint {

        @Test
        @DisplayName("동일 track 두 번째 저장 시 DataIntegrityViolationException 발생")
        void duplicateTrackViolatesConstraint() {
            persistCurriculum(Track.ANALYSIS);

            assertThatThrownBy(() -> {
                curriculumRepository.save(Curriculum.create(Track.ANALYSIS,
                        "[{\"step\":1,\"title\":\"제목\",\"desc\":\"설명\"}]"));
                curriculumRepository.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("서로 다른 track 은 정상 저장")
        void differentTracksAllowed() {
            persistCurriculum(Track.ANALYSIS);
            persistCurriculum(Track.ENGINEERING);

            assertThat(curriculumRepository.count()).isEqualTo(2);
        }
    }
}
