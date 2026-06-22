package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantEval;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.enums.MemberType;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class ApplicantEvalRepositoryTest extends TestcontainersBase {

    @Autowired ApplicantEvalRepository applicantEvalRepository;
    @Autowired TestEntityManager em;

    private Recruitment persistRecruitment(int term) {
        LocalDateTime now = LocalDateTime.now();
        return em.persistFlushFind(Recruitment.create(term, now.minusDays(1), now.plusDays(1), "{}", null));
    }

    private User persistUser(String pid) {
        return em.persistFlushFind(User.builder()
                .provider("kakao").providerId(pid).nickname("n-" + pid).memberType(MemberType.OUTSIDER).build());
    }

    private Applicant persistApplicant(Recruitment r, User u, Track track) {
        return em.persistFlushFind(Applicant.builder()
                .recruitment(r).user(u).status(Applicant.ApplicantStatus.SUBMITTED).track(track)
                .name("name").email("a@example.com").phone("01000000000").build());
    }

    private Admin persistAdmin(String username, Track track) {
        return em.persistFlushFind(Admin.builder()
                .username(username).password("p").role(Admin.Role.TEAM).name("name-" + username)
                .track(track).term(27).teamName(Admin.TeamName.서비스운영팀).createdBy(null).build());
    }

    @Nested
    @DisplayName("upsert (네이티브 INSERT ... ON DUPLICATE KEY UPDATE)")
    class Upsert {

        @Test
        @DisplayName("기존 행 없음 → INSERT")
        void insert() {
            Recruitment r = persistRecruitment(27);
            Applicant a = persistApplicant(r, persistUser("p1"), Track.ENGINEERING);
            Admin admin = persistAdmin("ev1", Track.ENGINEERING);
            em.clear();

            applicantEvalRepository.upsert(a.getId(), admin.getId(), "PASS", 9, "good");

            Optional<ApplicantEval> found = applicantEvalRepository.findByApplicantIdAndAdminId(a.getId(), admin.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getDecision()).isEqualTo(EvaluationDecision.PASS);
            assertThat(found.get().getScore()).isEqualTo(9);
            assertThat(found.get().getMemo()).isEqualTo("good");
        }

        @Test
        @DisplayName("기존 행 있음 → UPDATE (행 추가 없음)")
        void update() {
            Recruitment r = persistRecruitment(27);
            Applicant a = persistApplicant(r, persistUser("p1"), Track.ENGINEERING);
            Admin admin = persistAdmin("ev1", Track.ENGINEERING);
            em.clear();

            applicantEvalRepository.upsert(a.getId(), admin.getId(), "HOLD", 5, "maybe");
            applicantEvalRepository.upsert(a.getId(), admin.getId(), "PASS", 10, "changed");

            assertThat(applicantEvalRepository.count()).isEqualTo(1);
            ApplicantEval e = applicantEvalRepository.findByApplicantIdAndAdminId(a.getId(), admin.getId()).orElseThrow();
            assertThat(e.getDecision()).isEqualTo(EvaluationDecision.PASS);
            assertThat(e.getScore()).isEqualTo(10);
            assertThat(e.getMemo()).isEqualTo("changed");
        }

        @Test
        @DisplayName("같은 키 반복 호출 → 유니크 위반 없이 멱등(행 1개)")
        void idempotent() {
            Recruitment r = persistRecruitment(27);
            Applicant a = persistApplicant(r, persistUser("p1"), Track.ENGINEERING);
            Admin admin = persistAdmin("ev1", Track.ENGINEERING);
            em.clear();

            applicantEvalRepository.upsert(a.getId(), admin.getId(), "PASS", 8, "x");
            applicantEvalRepository.upsert(a.getId(), admin.getId(), "PASS", 8, "x");

            assertThat(applicantEvalRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("score null 저장 가능")
        void nullScore() {
            Recruitment r = persistRecruitment(27);
            Applicant a = persistApplicant(r, persistUser("p1"), Track.ENGINEERING);
            Admin admin = persistAdmin("ev1", Track.ENGINEERING);
            em.clear();

            applicantEvalRepository.upsert(a.getId(), admin.getId(), "PENDING", null, null);

            ApplicantEval e = applicantEvalRepository.findByApplicantIdAndAdminId(a.getId(), admin.getId()).orElseThrow();
            assertThat(e.getScore()).isNull();
            assertThat(e.getMemo()).isNull();
            assertThat(e.getDecision()).isEqualTo(EvaluationDecision.PENDING);
        }
    }

    @Nested
    @DisplayName("findByApplicantIdWithAdmin / findByRecruitmentId")
    class Queries {

        @Test
        @DisplayName("findByApplicantIdWithAdmin — 한 지원자의 평가 + admin JOIN FETCH")
        void byApplicantWithAdmin() {
            Recruitment r = persistRecruitment(27);
            Applicant a = persistApplicant(r, persistUser("p1"), Track.ENGINEERING);
            Admin ev1 = persistAdmin("ev1", Track.ENGINEERING);
            Admin ev2 = persistAdmin("ev2", Track.ENGINEERING);
            em.persistFlushFind(ApplicantEval.builder().applicant(a).admin(ev1).decision(EvaluationDecision.PASS).score(9).build());
            em.persistFlushFind(ApplicantEval.builder().applicant(a).admin(ev2).decision(EvaluationDecision.FAIL).score(2).build());
            em.clear();

            List<ApplicantEval> result = applicantEvalRepository.findByApplicantIdWithAdmin(a.getId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getAdmin().getName()).isNotNull(); // JOIN FETCH 로 즉시 로딩
        }

        @Test
        @DisplayName("findByRecruitmentId — 공고 내 모든 평가 (집계용)")
        void byRecruitment() {
            Recruitment r = persistRecruitment(27);
            Applicant a1 = persistApplicant(r, persistUser("p1"), Track.ENGINEERING);
            Applicant a2 = persistApplicant(r, persistUser("p2"), Track.ENGINEERING);
            Admin ev1 = persistAdmin("ev1", Track.ENGINEERING);
            em.persistFlushFind(ApplicantEval.builder().applicant(a1).admin(ev1).decision(EvaluationDecision.PASS).score(9).build());
            em.persistFlushFind(ApplicantEval.builder().applicant(a2).admin(ev1).decision(EvaluationDecision.HOLD).score(5).build());
            em.clear();

            List<ApplicantEval> result = applicantEvalRepository.findByRecruitmentId(r.getId());

            assertThat(result).hasSize(2);
        }
    }
}
