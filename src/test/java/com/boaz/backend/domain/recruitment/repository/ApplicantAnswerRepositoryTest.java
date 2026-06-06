package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion.Category;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion.Type;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
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
class ApplicantAnswerRepositoryTest extends TestcontainersBase {

    @Autowired ApplicantAnswerRepository answerRepository;
    @Autowired TestEntityManager em;

    private int userSeq = 0;

    private Recruitment persistRecruitment(int term) {
        LocalDateTime now = LocalDateTime.now();
        return em.persistFlushFind(Recruitment.create(term, now.minusDays(1), now.plusDays(1), "{}", null));
    }

    private User persistUser() {
        User u = User.builder()
                .provider("kakao").providerId("p" + (++userSeq))
                .nickname("nick").memberType(MemberType.OUTSIDER)
                .build();
        return em.persistFlushFind(u);
    }

    private Applicant persistApplicant(Recruitment r) {
        Applicant a = Applicant.builder()
                .recruitment(r).user(persistUser()).status(Applicant.ApplicantStatus.DRAFT)
                .track(Track.ENGINEERING).name("n").email("a@example.com").phone("01012345678")
                .build();
        return em.persistFlushFind(a);
    }

    private ApplicationQuestion persistQuestion(Recruitment r, int orderNum) {
        ApplicationQuestion q = ApplicationQuestion.create(
                r, "label" + orderNum, Category.COMMON, Type.TEXT, "content", null, 500, null, orderNum, true);
        return em.persistFlushFind(q);
    }

    private void persistAnswer(Applicant a, ApplicationQuestion q, String text) {
        em.persist(ApplicantAnswer.builder().applicant(a).question(q).answerText(text).build());
    }

    @Test
    @DisplayName("existsByQuestionId / findByApplicantIds")
    void existsAndFind() {
        Recruitment r = persistRecruitment(27);
        ApplicationQuestion q1 = persistQuestion(r, 1);
        ApplicationQuestion q2 = persistQuestion(r, 2);
        Applicant a = persistApplicant(r);
        persistAnswer(a, q1, "답1");
        persistAnswer(a, q2, "답2");
        em.flush();
        em.clear();

        assertThat(answerRepository.existsByQuestionId(q1.getId())).isTrue();

        List<ApplicantAnswer> answers = answerRepository.findByApplicantIds(List.of(a.getId()));
        assertThat(answers).hasSize(2);
    }

    @Test
    @DisplayName("deleteByApplicantId: 해당 지원자 답변만 벌크 삭제")
    void deleteByApplicantId() {
        Recruitment r = persistRecruitment(27);
        ApplicationQuestion q = persistQuestion(r, 1);
        Applicant a = persistApplicant(r);
        Applicant b = persistApplicant(r);
        persistAnswer(a, q, "A-답");
        persistAnswer(a, q, "A-답2");
        persistAnswer(b, q, "B-답");
        em.flush();

        answerRepository.deleteByApplicantId(a.getId());
        em.clear();

        assertThat(answerRepository.findByApplicantIds(List.of(a.getId()))).isEmpty();
        assertThat(answerRepository.findByApplicantIds(List.of(b.getId()))).hasSize(1);
    }

    @Test
    @DisplayName("deleteByRecruitmentId: 서브쿼리로 공고 단위 답변 벌크 삭제")
    void deleteByRecruitmentId() {
        Recruitment rA = persistRecruitment(27);
        Recruitment rB = persistRecruitment(26);
        ApplicationQuestion qA = persistQuestion(rA, 1);
        ApplicationQuestion qB = persistQuestion(rB, 1);
        Applicant aA = persistApplicant(rA);
        Applicant aB = persistApplicant(rB);
        persistAnswer(aA, qA, "A");
        persistAnswer(aB, qB, "B");
        em.flush();

        answerRepository.deleteByRecruitmentId(rA.getId());
        em.clear();

        assertThat(answerRepository.findByApplicantIds(List.of(aA.getId()))).isEmpty();
        assertThat(answerRepository.findByApplicantIds(List.of(aB.getId()))).hasSize(1);
    }
}
