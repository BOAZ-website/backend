package com.boaz.backend.domain.admin.repository;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.config.JpaConfig;
import com.boaz.backend.support.TestcontainersBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class AdminRepositoryTest extends TestcontainersBase {

    @Autowired AdminRepository adminRepository;
    @Autowired TestEntityManager em;

    private Admin persistAdmin(String username, Admin.Role role, Track track,
                               String name, Admin.TeamName team) {
        Admin a = Admin.builder()
                .username(username).password("ENC").role(role).name(name)
                .track(track).term(25).teamName(team).createdBy(null)
                .build();
        return em.persistFlushFind(a);
    }

    private Admin persistDeletedAdmin(String username, Admin.Role role, Track track,
                                      String name, Admin.TeamName team) {
        Admin a = persistAdmin(username, role, track, name, team);
        a.softDelete();
        em.flush();
        return a;
    }

    private void setCreatedAt(Long id, String createdAt) {
        EntityManager entityManager = em.getEntityManager();
        entityManager.createNativeQuery("UPDATE admin SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, id)
                .executeUpdate();
    }

    @Nested
    @DisplayName("findByUsernameAndDeletedAtIsNull")
    class FindByUsername {

        @Test
        @DisplayName("활성 계정만 username 으로 조회, soft delete 된 username 은 empty")
        void onlyActive() {
            persistAdmin("active", Admin.Role.TEAM, Track.ANALYSIS, "활성", Admin.TeamName.기획팀);
            persistDeletedAdmin("deleted", Admin.Role.TEAM, Track.ANALYSIS, "삭제됨", Admin.TeamName.기획팀);
            em.clear();

            assertThat(adminRepository.findByUsernameAndDeletedAtIsNull("active")).isPresent();
            assertThat(adminRepository.findByUsernameAndDeletedAtIsNull("deleted")).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByUsernameAndDeletedAtIsNull")
    class ExistsByUsername {

        @Test
        @DisplayName("활성 username 은 true, soft delete 된 username 은 false (재사용 가능)")
        void softDeleteReusable() {
            persistAdmin("active", Admin.Role.TEAM, Track.ANALYSIS, "활성", Admin.TeamName.기획팀);
            persistDeletedAdmin("recycled", Admin.Role.TEAM, Track.ANALYSIS, "삭제됨", Admin.TeamName.기획팀);
            em.clear();

            assertThat(adminRepository.existsByUsernameAndDeletedAtIsNull("active")).isTrue();
            assertThat(adminRepository.existsByUsernameAndDeletedAtIsNull("recycled")).isFalse();
            assertThat(adminRepository.existsByUsernameAndDeletedAtIsNull("none")).isFalse();
        }
    }

    @Nested
    @DisplayName("findAllByDeletedAtIsNullOrderByCreatedAtAsc")
    class FindAllOrdered {

        @Test
        @DisplayName("created_at 오름차순 + soft delete 제외")
        void orderedExcludingDeleted() {
            Admin a1 = persistAdmin("a1", Admin.Role.SUPER, Track.ANALYSIS, "first", Admin.TeamName.대표진);
            Admin a2 = persistAdmin("a2", Admin.Role.TEAM, Track.ANALYSIS, "second", Admin.TeamName.기획팀);
            Admin a3 = persistAdmin("a3", Admin.Role.TEAM, Track.ENGINEERING, "third", Admin.TeamName.기획팀);
            Admin deleted = persistAdmin("a4", Admin.Role.TEAM, Track.VISUALIZATION, "deleted", Admin.TeamName.기획팀);

            // @CreatedDate 가 동일 트랜잭션에서 같은 값일 수 있어 created_at 을 네이티브로 명시
            setCreatedAt(a1.getId(), "2026-03-01 10:00:00");
            setCreatedAt(a2.getId(), "2026-03-05 10:00:00");
            setCreatedAt(a3.getId(), "2026-03-10 10:00:00");
            setCreatedAt(deleted.getId(), "2026-03-07 10:00:00");
            deleted.softDelete();
            em.flush();
            em.clear();

            List<Admin> result = adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc();

            assertThat(result).extracting(Admin::getName).containsExactly("first", "second", "third");
        }

        @Test
        @DisplayName("활성 계정 없음 → 빈 리스트")
        void empty() {
            assertThat(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdAndDeletedAtIsNull")
    class FindById {

        @Test
        @DisplayName("활성 계정은 반환, soft delete 된 계정은 empty")
        void onlyActive() {
            Admin active = persistAdmin("active", Admin.Role.TEAM, Track.ANALYSIS, "활성", Admin.TeamName.기획팀);
            Admin deleted = persistDeletedAdmin("deleted", Admin.Role.TEAM, Track.ANALYSIS, "삭제됨", Admin.TeamName.기획팀);
            em.clear();

            assertThat(adminRepository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
            assertThat(adminRepository.findByIdAndDeletedAtIsNull(deleted.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("countByRoleAndDeletedAtIsNull")
    class CountByRole {

        @Test
        @DisplayName("역할별 활성 계정 수 (soft delete 제외)")
        void countExcludingDeleted() {
            persistAdmin("s1", Admin.Role.SUPER, Track.ANALYSIS, "super1", Admin.TeamName.대표진);
            persistAdmin("s2", Admin.Role.SUPER, Track.ANALYSIS, "super2", Admin.TeamName.대표진);
            persistDeletedAdmin("s3", Admin.Role.SUPER, Track.ANALYSIS, "super-deleted", Admin.TeamName.대표진);
            persistAdmin("t1", Admin.Role.TEAM, Track.ANALYSIS, "team1", Admin.TeamName.기획팀);
            em.clear();

            assertThat(adminRepository.countByRoleAndDeletedAtIsNull(Admin.Role.SUPER)).isEqualTo(2);
            assertThat(adminRepository.countByRoleAndDeletedAtIsNull(Admin.Role.TEAM)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findByTrackAndDeletedAtIsNullOrderByNameAsc")
    class FindByTrack {

        @Test
        @DisplayName("해당 부문 활성 평가자만 이름 오름차순")
        void byTrackOrdered() {
            persistAdmin("e1", Admin.Role.TEAM, Track.ENGINEERING, "나", Admin.TeamName.기획팀);
            persistAdmin("e2", Admin.Role.TEAM, Track.ENGINEERING, "가", Admin.TeamName.기획팀);
            persistAdmin("a1", Admin.Role.TEAM, Track.ANALYSIS, "다", Admin.TeamName.기획팀);
            persistDeletedAdmin("e3", Admin.Role.TEAM, Track.ENGINEERING, "라", Admin.TeamName.기획팀);
            em.clear();

            List<Admin> result = adminRepository.findByTrackAndDeletedAtIsNullOrderByNameAsc(Track.ENGINEERING);

            assertThat(result).extracting(Admin::getName).containsExactly("가", "나");
        }
    }

    @Nested
    @DisplayName("findEvaluatorPool")
    class FindEvaluatorPool {

        @Test
        @DisplayName("해당 부문 OR (전부문 권한 role+team) 평가자 풀, 이름순, soft delete 제외")
        void pool() {
            // 해당 부문(ENGINEERING)
            persistAdmin("e1", Admin.Role.TEAM, Track.ENGINEERING, "가", Admin.TeamName.기획팀);
            // 전부문 권한자 (role=SUPER AND team=차기대표진) — 본인 track 무관 포함
            persistAdmin("all", Admin.Role.SUPER, Track.ANALYSIS, "나", Admin.TeamName.차기대표진);
            // 제외 대상: 다른 부문 + 전부문 권한 아님
            persistAdmin("a1", Admin.Role.TEAM, Track.ANALYSIS, "다", Admin.TeamName.기획팀);
            // 제외 대상: ENGINEERING 이지만 soft delete
            persistDeletedAdmin("e2", Admin.Role.TEAM, Track.ENGINEERING, "라", Admin.TeamName.기획팀);
            em.clear();

            List<Admin> result = adminRepository.findEvaluatorPool(
                    Track.ENGINEERING, Admin.Role.SUPER, Admin.TeamName.차기대표진);

            assertThat(result).extracting(Admin::getName).containsExactly("가", "나");
        }
    }
}
