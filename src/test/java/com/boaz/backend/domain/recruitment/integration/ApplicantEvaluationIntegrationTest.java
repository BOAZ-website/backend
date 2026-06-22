package com.boaz.backend.domain.recruitment.integration;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.recruitment.dto.request.EvaluationSaveRequest;
import com.boaz.backend.domain.recruitment.dto.request.FinalDecisionUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.response.ApplicantEvaluationResponse;
import com.boaz.backend.domain.recruitment.dto.response.MyEvaluationResponse;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("지원서 평가 통합 테스트")
class ApplicantEvaluationIntegrationTest extends TestcontainersBase {

    @Autowired RecruitmentService recruitmentService;
    @Autowired RecruitmentRepository recruitmentRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired UserRepository userRepository;

    private int seq = 0;

    private Recruitment saveRecruitment(int term) {
        LocalDateTime now = LocalDateTime.now();
        return recruitmentRepository.save(Recruitment.create(term, now.minusDays(1), now.plusDays(1), "{}", null));
    }

    private Applicant saveApplicant(Recruitment r, Track track, Applicant.ApplicantStatus status) {
        User u = userRepository.save(User.builder()
                .provider("kakao").providerId("p" + (++seq)).nickname("n").memberType(MemberType.OUTSIDER).build());
        return applicantRepository.save(Applicant.builder()
                .recruitment(r).user(u).status(status).track(track)
                .name("name").email("a@example.com").phone("01000000000").build());
    }

    private Admin saveAdmin(Admin.Role role, Admin.TeamName team, Track track) {
        return adminRepository.save(Admin.builder()
                .username("adm" + (++seq)).password("p").role(role).name("name" + seq)
                .track(track).term(27).teamName(team).createdBy(null).build());
    }

    private EvaluationSaveRequest saveReq(EvaluationDecision d, Integer score, String memo) {
        EvaluationSaveRequest r = new EvaluationSaveRequest();
        ReflectionTestUtils.setField(r, "decision", d);
        ReflectionTestUtils.setField(r, "score", score);
        ReflectionTestUtils.setField(r, "memo", memo);
        return r;
    }

    private FinalDecisionUpdateRequest decisionReq(EvaluationDecision d) {
        FinalDecisionUpdateRequest r = new FinalDecisionUpdateRequest();
        ReflectionTestUtils.setField(r, "finalDecision", d);
        return r;
    }

    @Test
    @DisplayName("개인 평가 저장 → 평가 대시보드 집계에 반영")
    void saveThenAggregate() {
        Recruitment r = saveRecruitment(27);
        Applicant a = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin ev1 = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        Admin ev2 = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

        recruitmentService.saveMyEvaluation(a.getId(), saveReq(EvaluationDecision.PASS, 9, "good"), ev1);
        recruitmentService.saveMyEvaluation(a.getId(), saveReq(EvaluationDecision.HOLD, 5, "maybe"), ev2);

        List<ApplicantEvaluationResponse> dashboard = recruitmentService.getApplicantEvaluations(r.getId());
        ApplicantEvaluationResponse row = dashboard.stream()
                .filter(x -> x.getId().equals(a.getId())).findFirst().orElseThrow();

        assertThat(row.getPassCount()).isEqualTo(1);
        assertThat(row.getHoldCount()).isEqualTo(1);
        assertThat(row.getTotalScore()).isEqualTo(14);
    }

    @Test
    @DisplayName("개인 평가 저장은 upsert — 같은 평가자 재저장 시 행 추가 없이 갱신")
    void resaveIsUpsert() {
        Recruitment r = saveRecruitment(27);
        Applicant a = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin ev = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

        recruitmentService.saveMyEvaluation(a.getId(), saveReq(EvaluationDecision.HOLD, 5, "v1"), ev);
        MyEvaluationResponse second = recruitmentService.saveMyEvaluation(
                a.getId(), saveReq(EvaluationDecision.PASS, 10, "v2"), ev);

        assertThat(second.getDecision()).isEqualTo(EvaluationDecision.PASS);
        assertThat(second.getScore()).isEqualTo(10);
        // 같은 평가자의 평가는 1건만 (집계 개수로 검증)
        ApplicantEvaluationResponse row = recruitmentService.getApplicantEvaluations(r.getId()).stream()
                .filter(x -> x.getId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(row.getPassCount()).isEqualTo(1);
        assertThat(row.getHoldCount()).isZero();
    }

    @Test
    @DisplayName("타 부문 지원자 평가 저장 → ACCESS_DENIED")
    void crossTrackRejected() {
        Recruitment r = saveRecruitment(27);
        Applicant engApplicant = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin analysisAdmin = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);

        assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(
                engApplicant.getId(), saveReq(EvaluationDecision.PASS, 9, "x"), analysisAdmin))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("대표진 최종 평가 수정 → 대시보드 final_decision 반영")
    void finalDecisionByRepresentative() {
        Recruitment r = saveRecruitment(27);
        Applicant a = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin rep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.대표진, Track.ENGINEERING);

        recruitmentService.updateFinalDecision(a.getId(), decisionReq(EvaluationDecision.PASS), rep);

        ApplicantEvaluationResponse row = recruitmentService.getApplicantEvaluations(r.getId()).stream()
                .filter(x -> x.getId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(row.getFinalDecision()).isEqualTo(EvaluationDecision.PASS);
    }

    @Test
    @DisplayName("서비스운영팀 SUPER의 최종 평가 수정 → ACCESS_DENIED (대표진 아님)")
    void finalDecisionByNonRepresentative() {
        Recruitment r = saveRecruitment(27);
        Applicant a = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin superOps = saveAdmin(Admin.Role.SUPER, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

        assertThatThrownBy(() -> recruitmentService.updateFinalDecision(
                a.getId(), decisionReq(EvaluationDecision.PASS), superOps))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
