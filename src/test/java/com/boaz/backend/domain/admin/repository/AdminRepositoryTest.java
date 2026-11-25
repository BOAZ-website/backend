package com.boaz.backend.domain.admin.repository;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.member.entity.Member;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.config.JpaConfig;
import com.boaz.backend.support.TestcontainersBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        @DisplayName("TC-001 활성 계정만 username 으로 조회, soft delete 된 username 은 empty")
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
        @DisplayName("TC-002 활성 username 은 true, soft delete 된 username 은 false (재사용 가능)")
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
        @DisplayName("TC-003 created_at 오름차순 + soft delete 제외")
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
        @DisplayName("TC-004 활성 계정 없음 → 빈 리스트")
        void empty() {
            assertThat(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdAndDeletedAtIsNull")
    class FindById {

        @Test
        @DisplayName("TC-005 활성 계정은 반환, soft delete 된 계정은 empty")
        void onlyActive() {
            Admin active = persistAdmin("active", Admin.Role.TEAM, Track.ANALYSIS, "활성", Admin.TeamName.기획팀);
            Admin deleted = persistDeletedAdmin("deleted", Admin.Role.TEAM, Track.ANALYSIS, "삭제됨", Admin.TeamName.기획팀);
            em.clear();

            assertThat(adminRepository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
            assertThat(adminRepository.findByIdAndDeletedAtIsNull(deleted.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByTrackAndDeletedAtIsNullOrderByNameAsc")
    class FindByTrack {

        @Test
        @DisplayName("TC-006 해당 부문 활성 평가자만 이름 오름차순")
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

    /**
     * V2.2.2~V2.2.4에서 추가한 member_id·live_member_id·is_leader의 DB 제약.
     * 위반 케이스는 TestEntityManager로 저장해 JPA 예외(PersistenceException)를 그대로 본다.
     */
    @Nested
    @DisplayName("member 연결 컬럼 제약")
    class MemberLinkColumns {

        private Long persistMember() {
            Member m = Member.builder()
                    .term(25).track(Track.ANALYSIS).name("회원").status(Member.Status.ACTIVE)
                    .build();
            return em.persistAndFlush(m).getId();
        }

        private Admin newAdmin(String username, Long memberId) {
            return Admin.builder()
                    .username(username).password("ENC").role(Admin.Role.TEAM).name("회원")
                    .track(Track.ANALYSIS).term(25).teamName(Admin.TeamName.기획팀)
                    .memberId(memberId)
                    .build();
        }

        private Object liveMemberId(Long adminId) {
            return em.getEntityManager()
                    .createNativeQuery("SELECT live_member_id FROM admin WHERE id = ?1")
                    .setParameter(1, adminId)
                    .getSingleResult();
        }

        @Test
        @DisplayName("TC-007 member_id 없이 저장 가능(백필 전 기존 계정), is_leader 기본 false")
        void memberIdNullable() {
            Admin a = em.persistFlushFind(newAdmin("no_member1", null));
            em.persistAndFlush(newAdmin("no_member2", null));

            assertThat(a.getMemberId()).isNull();
            assertThat(a.isLeader()).isFalse();
            assertThat(liveMemberId(a.getId())).isNull();
        }

        @Test
        @DisplayName("TC-008 [예외] 같은 회원에 삭제되지 않은 계정 두 개 → uk_admin_live_member 위반")
        void oneLiveAccountPerMember() {
            Long memberId = persistMember();
            em.persistAndFlush(newAdmin("first", memberId));

            assertThatThrownBy(() -> em.persistAndFlush(newAdmin("second", memberId)))
                    .isInstanceOf(PersistenceException.class)
                    .rootCause().hasMessageContaining("uk_admin_live_member");
        }

        @Test
        @DisplayName("TC-009 계정을 soft delete하면 member_id는 남고 live_member_id는 NULL → 같은 회원에 새 계정 가능")
        void deletedAccountLeavesUnique() {
            Long memberId = persistMember();
            Admin old = em.persistAndFlush(newAdmin("old", memberId));
            old.softDelete();
            em.flush();

            Admin renewed = em.persistAndFlush(newAdmin("renewed", memberId));
            em.clear();

            assertThat(em.find(Admin.class, old.getId()).getMemberId()).isEqualTo(memberId);
            assertThat(liveMemberId(old.getId())).isNull();
            assertThat(((Number) liveMemberId(renewed.getId())).longValue()).isEqualTo(memberId);
        }

        @Test
        @DisplayName("TC-010 [예외] 없는 member id 참조 → fk_admin_member 위반")
        void memberFk() {
            assertThatThrownBy(() -> em.persistAndFlush(newAdmin("ghost", 999_999L)))
                    .isInstanceOf(PersistenceException.class)
                    .rootCause().hasMessageContaining("fk_admin_member");
        }

        @Test
        @DisplayName("TC-011 is_leader를 생략한 SQL INSERT(직전 버전 앱) → DB 기본값 false")
        void isLeaderDbDefault() {
            em.getEntityManager().createNativeQuery(
                    "INSERT INTO admin (username, password, role, name, track, term, team_name) "
                            + "VALUES ('legacy', 'ENC', 'TEAM', '구버전', 'ANALYSIS', 25, '기획팀')")
                    .executeUpdate();
            em.clear();

            Admin legacy = adminRepository.findByUsernameAndDeletedAtIsNull("legacy").orElseThrow();
            assertThat(legacy.isLeader()).isFalse();
            assertThat(legacy.getMemberId()).isNull();
        }

        @Test
        @DisplayName("TC-012 is_leader=true 저장 → 그대로 조회")
        void isLeaderTrue() {
            Admin leader = Admin.builder()
                    .username("leader").password("ENC").role(Admin.Role.TEAM).name("팀장")
                    .track(Track.ANALYSIS).term(25).teamName(Admin.TeamName.기획팀)
                    .isLeader(true)
                    .build();
            Admin found = em.persistFlushFind(leader);

            assertThat(found.isLeader()).isTrue();
        }
    }
}
