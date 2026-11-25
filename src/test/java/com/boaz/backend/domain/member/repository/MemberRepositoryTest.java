package com.boaz.backend.domain.member.repository;

import com.boaz.backend.domain.member.entity.Member;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.config.JpaConfig;
import com.boaz.backend.support.TestcontainersBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * member 테이블의 DB 제약(V2.2.1)을 실제 MySQL에서 확인한다.
 * 컬럼 이름·타입은 Flyway 실행 뒤 Hibernate validate가 확인하므로 여기서 다루지 않는다.
 * 위반 케이스는 TestEntityManager로 저장해 Spring 예외 변환 없이 JPA 예외(PersistenceException)를 그대로 본다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
@DisplayName("member 테이블 제약")
class MemberRepositoryTest extends TestcontainersBase {

    @Autowired MemberRepository memberRepository;
    @Autowired TestEntityManager em;

    private Member.MemberBuilder member(Track track, Long sourceUserId) {
        return Member.builder()
                .term(27).track(track).name("김보아즈").status(Member.Status.ACTIVE)
                .sourceUserId(sourceUserId);
    }

    private int insertBySql(String sql) {
        EntityManager entityManager = em.getEntityManager();
        return entityManager.createNativeQuery(sql).executeUpdate();
    }

    @Test
    @DisplayName("TC-001 JPA 저장 → version 0, created_at·updated_at 기록, 값이 그대로 조회됨")
    void saveByJpa() {
        Member saved = memberRepository.saveAndFlush(member(Track.ANALYSIS, null)
                .nameSuffix("01").phone("010-0000-0000").birthDate(LocalDate.of(2001, 5, 10))
                .build());
        em.clear();

        Member found = memberRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getVersion()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getTrack()).isEqualTo(Track.ANALYSIS);
        assertThat(found.getStatus()).isEqualTo(Member.Status.ACTIVE);
        assertThat(found.getNameSuffix()).isEqualTo("01");
        assertThat(found.getPhone()).isEqualTo("010-0000-0000");
        assertThat(found.getBirthDate()).isEqualTo(LocalDate.of(2001, 5, 10));
        assertThat(found.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("TC-002 SQL INSERT에서 version·created_at·updated_at 생략 → DB 기본값(0, 현재 시각)")
    void dbDefaultsForSqlBackfill() {
        insertBySql("INSERT INTO member (term, track, name, status) VALUES (19, 'ANALYSIS', '백필', 'COMPLETED')");
        em.clear();

        Object[] row = (Object[]) em.getEntityManager()
                .createNativeQuery("SELECT version, created_at, updated_at FROM member WHERE name = '백필'")
                .getSingleResult();
        assertThat(((Number) row[0]).longValue()).isZero();
        assertThat(row[1]).isNotNull();
        assertThat(row[2]).isNotNull();
    }

    @Test
    @DisplayName("TC-003 [예외] track=ALL 저장 → ck_member_track 위반")
    void trackAllRejected() {
        assertThatThrownBy(() -> em.persistAndFlush(member(Track.ALL, null).build()))
                .isInstanceOf(PersistenceException.class)
                .rootCause().hasMessageContaining("ck_member_track");
    }

    @Test
    @DisplayName("TC-004 [예외] 다섯 회원 상태 밖의 status(SQL) → ck_member_status 위반")
    void unknownStatusRejected() {
        assertThatThrownBy(() -> insertBySql(
                "INSERT INTO member (term, track, name, status) VALUES (27, 'ANALYSIS', '상태오류', 'DELETED')"))
                .isInstanceOf(PersistenceException.class)
                .rootCause().hasMessageContaining("ck_member_status");
    }

    @Test
    @DisplayName("TC-005 source_user_id NULL은 여러 행 허용, 같은 값 두 번 → uk_member_source_user 위반")
    void sourceUserUnique() {
        memberRepository.saveAndFlush(member(Track.ANALYSIS, null).build());
        memberRepository.saveAndFlush(member(Track.ANALYSIS, null).build());
        memberRepository.saveAndFlush(member(Track.ANALYSIS, 100L).build());

        assertThatThrownBy(() -> em.persistAndFlush(member(Track.ENGINEERING, 100L).build()))
                .isInstanceOf(PersistenceException.class)
                .rootCause().hasMessageContaining("uk_member_source_user");
    }
}
