package com.boaz.backend.domain.recruitment.integration;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.entity.AdminPermissionOverride;
import com.boaz.backend.domain.admin.repository.AdminPermissionOverrideRepository;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.recruitment.dto.request.EvaluationSaveRequest;
import com.boaz.backend.domain.recruitment.dto.request.FinalDecisionUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.response.ApplicantAnswersResponse;
import com.boaz.backend.domain.recruitment.dto.response.ApplicantEvaluationResponse;
import com.boaz.backend.domain.recruitment.dto.response.ApplicantEvaluatorsResponse;
import com.boaz.backend.domain.recruitment.dto.response.EvaluatorEvaluationResponse;
import com.boaz.backend.domain.recruitment.dto.response.EvaluatorInterviewQuestionResponse;
import com.boaz.backend.domain.recruitment.dto.response.ApplicantInterviewQuestionsResponse;
import com.boaz.backend.domain.recruitment.dto.response.MyEvaluationResponse;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.repository.ApplicantAnswerRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicationQuestionRepository;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.domain.recruitment.service.RecruitmentService;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.JwtProvider;
import com.boaz.backend.global.security.authz.EffectivePermissions;
import com.boaz.backend.global.security.authz.Permission;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@DisplayName("지원서 평가 통합 테스트")
class ApplicantEvaluationIntegrationTest extends TestcontainersBase {

    @Autowired RecruitmentService recruitmentService;
    @Autowired RecruitmentRepository recruitmentRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired ApplicationQuestionRepository applicationQuestionRepository;
    @Autowired ApplicantAnswerRepository applicantAnswerRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired UserRepository userRepository;
    @Autowired AdminPermissionOverrideRepository overrideRepository;
    @Autowired EffectivePermissions effectivePermissions;
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    // ── 주체의 유효 권한 — 최종 권한 매트릭스의 해당 열을 그대로 옮긴 것 (recruitment 평가 관련 행만) ──
    // 컨트롤러가 AdminUserDetails.getPermissions() 로 넘기는 자리다. role/teamName 이 아니라 이 집합이 판정을 가른다.
    /** 운영진(서비스운영팀·운영지원팀·기타 운영진) — 본인 부문 평가 + 최종 합불 조회. */
    private static final Set<Permission> STAFF = Set.of(
            Permission.EVALUATION_OWN_TRACK_WRITE, Permission.FINAL_DECISION_READ);
    /** 대표진 — 본인 부문 평가 + 최종 합불 조회/CUD. */
    private static final Set<Permission> REP = Set.of(
            Permission.EVALUATION_OWN_TRACK_WRITE, Permission.FINAL_DECISION_READ, Permission.FINAL_DECISION_WRITE);
    /** 차기대표진 — 대표진 + 다른 부문 평가. */
    private static final Set<Permission> NEXT_REP = Set.of(
            Permission.EVALUATION_OWN_TRACK_WRITE, Permission.EVALUATION_ALL_TRACK_WRITE,
            Permission.FINAL_DECISION_READ, Permission.FINAL_DECISION_WRITE);

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
        return saveReq(d, score, memo, null);
    }

    private EvaluationSaveRequest saveReq(EvaluationDecision d, Integer score, String memo, String interviewQuestion) {
        EvaluationSaveRequest r = new EvaluationSaveRequest();
        ReflectionTestUtils.setField(r, "decision", d);
        ReflectionTestUtils.setField(r, "score", score);
        ReflectionTestUtils.setField(r, "memo", memo);
        ReflectionTestUtils.setField(r, "interviewQuestion", interviewQuestion);
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

        recruitmentService.saveMyEvaluation(a.getId(), saveReq(EvaluationDecision.PASS, 9, "good"), ev1, STAFF);
        recruitmentService.saveMyEvaluation(a.getId(), saveReq(EvaluationDecision.HOLD, 5, "maybe"), ev2, STAFF);

        List<ApplicantEvaluationResponse> dashboard = recruitmentService.getApplicantEvaluations(r.getId(), ev1, STAFF);
        ApplicantEvaluationResponse row = dashboard.stream()
                .filter(x -> x.getId().equals(a.getId())).findFirst().orElseThrow();

        assertThat(row.getPassCount()).isEqualTo(1);
        assertThat(row.getHoldCount()).isEqualTo(1);
        assertThat(row.getTotalScore()).isEqualTo(14);
        assertThat(row.getMyDecision()).isEqualTo(EvaluationDecision.PASS);   // 본인(ev1) 평가 = PASS
    }

    @Test
    @DisplayName("개인 평가 저장은 upsert — 같은 평가자 재저장 시 행 추가 없이 갱신")
    void resaveIsUpsert() {
        Recruitment r = saveRecruitment(27);
        Applicant a = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin ev = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

        recruitmentService.saveMyEvaluation(a.getId(), saveReq(EvaluationDecision.HOLD, 5, "v1"), ev, STAFF);
        MyEvaluationResponse second = recruitmentService.saveMyEvaluation(
                a.getId(), saveReq(EvaluationDecision.PASS, 10, "v2"), ev, STAFF);

        assertThat(second.getDecision()).isEqualTo(EvaluationDecision.PASS);
        assertThat(second.getScore()).isEqualTo(10);
        // 같은 평가자의 평가는 1건만 (집계 개수로 검증)
        ApplicantEvaluationResponse row = recruitmentService.getApplicantEvaluations(r.getId(), ev, STAFF).stream()
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
                engApplicant.getId(), saveReq(EvaluationDecision.PASS, 9, "x"), analysisAdmin, STAFF))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("대표진 최종 평가 수정 → 대시보드 final_decision 반영")
    void finalDecisionByRepresentative() {
        Recruitment r = saveRecruitment(27);
        Applicant a = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin rep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.대표진, Track.ENGINEERING);

        recruitmentService.updateFinalDecision(a.getId(), decisionReq(EvaluationDecision.PASS));

        ApplicantEvaluationResponse row = recruitmentService.getApplicantEvaluations(r.getId(), rep, REP).stream()
                .filter(x -> x.getId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(row.getFinalDecision()).isEqualTo(EvaluationDecision.PASS);
    }

    // ── 최종 합불 수정 — 실제 필터체인(JWT → AdminUserDetailsService → EffectivePermissions + override 행
    //    → @PreAuthorize FINAL_DECISION_WRITE → 서비스)을 태운다. 최종 합불 CUD 는 독립 permission 이라
    //    서류 평가 부문 범위(EVALUATION_*_TRACK_WRITE)와 무관하게 FINAL_DECISION_WRITE 하나로만 갈린다.

    private void override(Admin admin, Permission permission, AdminPermissionOverride.Effect effect) {
        overrideRepository.save(AdminPermissionOverride.builder()
                .adminId(admin.getId()).permission(permission).effect(effect).grantedBy(admin.getId()).build());
    }

    private ResultActions patchFinalDecision(Admin admin, Long applicantId, EvaluationDecision decision) throws Exception {
        String token = jwtProvider.generateAdminAccessToken(admin.getId(), admin.getUsername(), admin.getRole().name());
        return mockMvc.perform(patch("/api/v1/admin/recruitment/applicants/" + applicantId + "/final-decision")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"final_decision\":\"" + decision.name() + "\"}"));
    }

    private EvaluationDecision finalDecisionOf(Applicant a) {
        return applicantRepository.findById(a.getId()).orElseThrow().getFinalDecision();
    }

    @Test
    @DisplayName("[최종합불 A] 대표진(분석, 전 부문 평가 권한 없음) → 엔지 지원자 최종 합불 수정 200")
    void finalDecisionRepresentativeCrossTrack() throws Exception {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin rep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.대표진, Track.ANALYSIS);
        assertThat(effectivePermissions.of(rep))
                .contains(Permission.FINAL_DECISION_WRITE)
                .doesNotContain(Permission.EVALUATION_ALL_TRACK_WRITE);

        patchFinalDecision(rep, eng.getId(), EvaluationDecision.PASS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.final_decision").value("PASS"));
        assertThat(finalDecisionOf(eng)).isEqualTo(EvaluationDecision.PASS);
    }

    @Test
    @DisplayName("[최종합불 B] 차기대표진(분석) → 엔지 지원자 최종 합불 수정 200 (regression)")
    void finalDecisionNextRepresentativeCrossTrack() throws Exception {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin nextRep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ANALYSIS);

        patchFinalDecision(nextRep, eng.getId(), EvaluationDecision.FAIL).andExpect(status().isOk());
        assertThat(finalDecisionOf(eng)).isEqualTo(EvaluationDecision.FAIL);
    }

    @Test
    @DisplayName("[최종합불 C] FINAL_DECISION_WRITE 없음 → 같은 track 이어도, 전 부문 평가 GRANT 가 있어도 403")
    void finalDecisionWithoutPermissionDenied() throws Exception {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        // 같은 track 운영진 — 본인 부문 평가 권한은 있다
        Admin sameTrackStaff = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        // 타 track 운영진 + 전 부문 평가 GRANT — 평가 범위는 넓어도 최종 합불 권한은 아니다
        Admin allTrackStaff = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
        override(allTrackStaff, Permission.EVALUATION_ALL_TRACK_WRITE, AdminPermissionOverride.Effect.GRANT);

        patchFinalDecision(sameTrackStaff, eng.getId(), EvaluationDecision.PASS)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        patchFinalDecision(allTrackStaff, eng.getId(), EvaluationDecision.PASS)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        assertThat(finalDecisionOf(eng)).isEqualTo(EvaluationDecision.PENDING);
    }

    @Test
    @DisplayName("[최종합불 D] 기본값에 없는 운영진(기획팀)에 FINAL_DECISION_WRITE GRANT → 타 track 지원자 수정 200")
    void finalDecisionCustomGrant() throws Exception {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin staff = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, Track.ANALYSIS);
        assertThat(effectivePermissions.of(staff)).doesNotContain(Permission.FINAL_DECISION_WRITE);   // 기본값 X

        override(staff, Permission.FINAL_DECISION_WRITE, AdminPermissionOverride.Effect.GRANT);

        patchFinalDecision(staff, eng.getId(), EvaluationDecision.HOLD).andExpect(status().isOk());
        assertThat(finalDecisionOf(eng)).isEqualTo(EvaluationDecision.HOLD);
    }

    @Test
    @DisplayName("[최종합불 E] 대표진에서 FINAL_DECISION_WRITE REVOKE → 같은 track 지원자여도 403")
    void finalDecisionCustomRevoke() throws Exception {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin rep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.대표진, Track.ENGINEERING);
        override(rep, Permission.FINAL_DECISION_WRITE, AdminPermissionOverride.Effect.REVOKE);
        // 평가 권한은 그대로 — 같은 track 평가는 가능하지만 최종 합불은 아니다
        assertThat(effectivePermissions.of(rep)).contains(Permission.EVALUATION_OWN_TRACK_WRITE);

        patchFinalDecision(rep, eng.getId(), EvaluationDecision.PASS)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));
        assertThat(finalDecisionOf(eng)).isEqualTo(EvaluationDecision.PENDING);
    }

    @Test
    @DisplayName("평가자 풀 = 해당 부문 + 차기 대표진 — 엔지 지원자 풀에 타 부문 차기대표진 포함")
    void evaluatorPoolIncludesNextRepresentative() {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin engEvaluator = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        Admin analysisNextRep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ANALYSIS);
        Admin analysisNormal = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, Track.ANALYSIS);

        // 지원서별 평가 조회 (자기 부문이라 조회 가능한 엔지 평가자로 호출)
        var res = recruitmentService.getApplicantEvaluators(eng.getId(), engEvaluator, STAFF);

        assertThat(res.getEvaluations()).extracting(e -> e.getAdminId())
                .contains(engEvaluator.getId(), analysisNextRep.getId())   // 엔지 평가자 + 타 부문 차기대표진 포함
                .doesNotContain(analysisNormal.getId());                   // 타 부문 일반 운영진은 제외

        // 면접 질문 조회도 동일 풀
        var iq = recruitmentService.getApplicantInterviewQuestions(eng.getId(), engEvaluator, STAFF);
        assertThat(iq.getInterviewQuestions()).extracting(q -> q.getAdminId())
                .contains(engEvaluator.getId(), analysisNextRep.getId())
                .doesNotContain(analysisNormal.getId());
    }

    @Test
    @DisplayName("면접 질문 저장(개인 평가) → 지원서별 면접 질문 조회 & 개인 평가 조회에 반영")
    void interviewQuestionSaveAndQuery() {
        Recruitment r = saveRecruitment(27);
        Applicant a = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin ev1 = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        Admin ev2 = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

        recruitmentService.saveMyEvaluation(a.getId(),
                saveReq(EvaluationDecision.PASS, 9, "good", "콜드스타트 문제를 어떻게 해결했나요?"), ev1, STAFF);

        // 개인 평가 조회에 면접 질문 포함 (라운드트립)
        MyEvaluationResponse mine = recruitmentService.getMyEvaluation(a.getId(), ev1, STAFF);
        assertThat(mine.getInterviewQuestion()).isEqualTo("콜드스타트 문제를 어떻게 해결했나요?");

        // 지원서별 면접 질문 조회 — 평가자 풀 전체, 미작성자(ev2)는 null
        ApplicantInterviewQuestionsResponse res =
                recruitmentService.getApplicantInterviewQuestions(a.getId(), ev1, STAFF);
        assertThat(res.getApplicantId()).isEqualTo(a.getId());
        assertThat(res.getInterviewQuestions()).hasSize(2);
        assertThat(res.getInterviewQuestions())
                .anySatisfy(q -> {
                    assertThat(q.getAdminId()).isEqualTo(ev1.getId());
                    assertThat(q.getInterviewQuestion()).isEqualTo("콜드스타트 문제를 어떻게 해결했나요?");
                })
                .anySatisfy(q -> {
                    assertThat(q.getAdminId()).isEqualTo(ev2.getId());
                    assertThat(q.getInterviewQuestion()).isNull();
                });
    }

    @Test
    @DisplayName("지원서 답변 조회 — 문항 순서대로 답변 + 문항 정보 반환 (TEXT/TABLE)")
    void getApplicantAnswers() {
        Recruitment r = saveRecruitment(27);
        Applicant a = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        ApplicationQuestion q1 = applicationQuestionRepository.save(ApplicationQuestion.create(
                r, "공통1", ApplicationQuestion.Category.COMMON, ApplicationQuestion.Type.TEXT,
                "지원 동기", null, 500, null, 1, true));
        ApplicationQuestion q2 = applicationQuestionRepository.save(ApplicationQuestion.create(
                r, "엔지1", ApplicationQuestion.Category.ENGINEERING, ApplicationQuestion.Type.TABLE,
                "기술 스택", null, null, "{\"multiple\":false}", 2, true));
        // 일부러 순서를 뒤섞어 저장
        applicantAnswerRepository.save(ApplicantAnswer.builder()
                .applicant(a).question(q2).answerJson("{\"Python\":\"능숙\"}").build());
        applicantAnswerRepository.save(ApplicantAnswer.builder()
                .applicant(a).question(q1).answerText("저는 ~~").build());
        Admin viewer = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

        ApplicantAnswersResponse res = recruitmentService.getApplicantAnswers(a.getId(), viewer, STAFF);

        assertThat(res.getApplicantId()).isEqualTo(a.getId());
        assertThat(res.getAnswers()).hasSize(2);
        // orderNum 1(q1) 먼저
        ApplicantAnswersResponse.AnswerDetailResponse first = res.getAnswers().get(0);
        assertThat(first.getQuestionId()).isEqualTo(q1.getId());
        assertThat(first.getType()).isEqualTo(ApplicationQuestion.Type.TEXT);
        assertThat(first.getAnswer().asText()).isEqualTo("저는 ~~");
        ApplicantAnswersResponse.AnswerDetailResponse second = res.getAnswers().get(1);
        assertThat(second.getType()).isEqualTo(ApplicationQuestion.Type.TABLE);
        assertThat(second.getAnswer().get("Python").asText()).isEqualTo("능숙");
    }

    @Test
    @DisplayName("타 부문 지원서 답변 조회 — 본인 부문 권한만 있으면(운영진·현재 대표진) 차단, 전 부문 권한(차기 대표진)만 가능")
    void getApplicantAnswersTrackAccess() {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin analysisAdmin = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
        Admin currentRep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.대표진, Track.ANALYSIS);
        Admin nextRep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ANALYSIS);

        // 운영진(분석, 본인 부문 권한만)이 엔지 지원자 답변 조회 → 차단
        assertThatThrownBy(() -> recruitmentService.getApplicantAnswers(eng.getId(), analysisAdmin, STAFF))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);

        // 현재 대표진(분석)도 전 부문 권한이 없어 차단 (본인 track만)
        assertThatThrownBy(() -> recruitmentService.getApplicantAnswers(eng.getId(), currentRep, REP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);

        // 차기 대표진은 전 부문 권한이 있어 타 부문이어도 조회 가능
        ApplicantAnswersResponse res = recruitmentService.getApplicantAnswers(eng.getId(), nextRep, NEXT_REP);
        assertThat(res.getApplicantId()).isEqualTo(eng.getId());
        assertThat(res.getAnswers()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 지원자 답변 조회 → APPLICATION_NOT_FOUND")
    void getApplicantAnswersNotFound() {
        Admin viewer = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        assertThatThrownBy(() -> recruitmentService.getApplicantAnswers(999999L, viewer, STAFF))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    // ── 평가자 풀 — 실제 오버라이드 행 + EffectivePermissions 로 계산 ──────────────
    // 권한 커스텀 API 가 아니라 override 행을 직접 저장한다. 호출 주체의 권한도 하드코딩 상수가 아니라
    // effectivePermissions.of(admin) 로 계산해 컨트롤러가 넘기는 값과 같게 맞춘다.

    private void grant(Admin admin, Permission permission) {
        overrideRepository.save(AdminPermissionOverride.builder()
                .adminId(admin.getId()).permission(permission)
                .effect(AdminPermissionOverride.Effect.GRANT).grantedBy(admin.getId()).build());
    }

    private List<Long> evaluatorIds(Long applicantId, Admin viewer) {
        return recruitmentService.getApplicantEvaluators(applicantId, viewer, effectivePermissions.of(viewer))
                .getEvaluations().stream().map(EvaluatorEvaluationResponse::getAdminId).toList();
    }

    private List<Long> interviewerIds(Long applicantId, Admin viewer) {
        return recruitmentService.getApplicantInterviewQuestions(applicantId, viewer, effectivePermissions.of(viewer))
                .getInterviewQuestions().stream().map(EvaluatorInterviewQuestionResponse::getAdminId).toList();
    }

    @Test
    @DisplayName("[A] 일반 운영진(분석)에 전 부문 평가 GRANT → 엔지 지원자 평가 저장, 평가 상세에 본인·평가 내용 표시")
    void customAllTrackGrantShownInEvaluators() {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin custom = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
        grant(custom, Permission.EVALUATION_ALL_TRACK_WRITE);
        Set<Permission> perms = effectivePermissions.of(custom);
        assertThat(perms).contains(Permission.EVALUATION_ALL_TRACK_WRITE);

        recruitmentService.saveMyEvaluation(eng.getId(),
                saveReq(EvaluationDecision.PASS, 9, "타 부문 평가", "왜 엔지니어링인가요?"), custom, perms);

        ApplicantEvaluatorsResponse res = recruitmentService.getApplicantEvaluators(eng.getId(), custom, perms);
        assertThat(res.getEvaluations()).filteredOn(e -> e.getAdminId().equals(custom.getId()))
                .singleElement().satisfies(e -> {
                    assertThat(e.getDecision()).isEqualTo(EvaluationDecision.PASS);
                    assertThat(e.getScore()).isEqualTo(9);
                    assertThat(e.getMemo()).isEqualTo("타 부문 평가");
                });
    }

    @Test
    @DisplayName("[B] 전 부문 평가 GRANT 운영진 — 면접 질문 조회에서도 누락되지 않음 (작성 전·후)")
    void customAllTrackGrantShownInInterviewQuestions() {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin custom = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
        grant(custom, Permission.EVALUATION_ALL_TRACK_WRITE);
        Set<Permission> perms = effectivePermissions.of(custom);

        // 작성 전 — 현재 평가 가능자라 미작성(null)으로라도 풀에 있어야 한다
        assertThat(interviewerIds(eng.getId(), custom)).contains(custom.getId());

        recruitmentService.saveMyEvaluation(eng.getId(),
                saveReq(EvaluationDecision.HOLD, 6, "m", "분산 처리 경험이 있나요?"), custom, perms);

        ApplicantInterviewQuestionsResponse res =
                recruitmentService.getApplicantInterviewQuestions(eng.getId(), custom, perms);
        assertThat(res.getInterviewQuestions()).filteredOn(q -> q.getAdminId().equals(custom.getId()))
                .singleElement()
                .satisfies(q -> assertThat(q.getInterviewQuestion()).isEqualTo("분산 처리 경험이 있나요?"));
    }

    @Test
    @DisplayName("[C] 평가 작성 후 전 부문 GRANT 제거 → 재평가 ACCESS_DENIED, 그래도 기존 작성자로 상세·면접 질문에 계속 표시")
    void grantRemovedAuthorStillShown() {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin custom = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
        Admin engStaff = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        grant(custom, Permission.EVALUATION_ALL_TRACK_WRITE);
        recruitmentService.saveMyEvaluation(eng.getId(),
                saveReq(EvaluationDecision.FAIL, 2, "권한 회수 전 작성", "Q"), custom, effectivePermissions.of(custom));

        overrideRepository.deleteByAdminId(custom.getId());
        Set<Permission> after = effectivePermissions.of(custom);
        assertThat(after).doesNotContain(Permission.EVALUATION_ALL_TRACK_WRITE);

        // 현재는 이 지원자를 평가할 권한 없음
        assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(eng.getId(),
                saveReq(EvaluationDecision.PASS, 9, "재평가", null), custom, after))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);

        // 엔지 평가자가 보는 상세 — 기존 작성자라 계속 표시, 내용도 유지
        ApplicantEvaluatorsResponse res = recruitmentService.getApplicantEvaluators(
                eng.getId(), engStaff, effectivePermissions.of(engStaff));
        assertThat(res.getEvaluations()).filteredOn(e -> e.getAdminId().equals(custom.getId()))
                .singleElement().satisfies(e -> {
                    assertThat(e.getDecision()).isEqualTo(EvaluationDecision.FAIL);
                    assertThat(e.getMemo()).isEqualTo("권한 회수 전 작성");
                });
        assertThat(interviewerIds(eng.getId(), engStaff)).contains(custom.getId(), engStaff.getId());
    }

    @Test
    @DisplayName("[D] 차기대표진(기본 권한) 타 부문 평가 → 저장 성공, 상세·면접 질문 풀에 유지 (regression)")
    void nextRepresentativeCrossTrackRegression() {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin nextRep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ANALYSIS);
        Set<Permission> perms = effectivePermissions.of(nextRep);   // 오버라이드 없음 — 기본 세트만

        recruitmentService.saveMyEvaluation(eng.getId(),
                saveReq(EvaluationDecision.PASS, 10, "차기대표진 평가", "Q2"), nextRep, perms);

        ApplicantEvaluatorsResponse res = recruitmentService.getApplicantEvaluators(eng.getId(), nextRep, perms);
        assertThat(res.getEvaluations()).filteredOn(e -> e.getAdminId().equals(nextRep.getId()))
                .singleElement()
                .satisfies(e -> assertThat(e.getMemo()).isEqualTo("차기대표진 평가"));
        assertThat(interviewerIds(eng.getId(), nextRep)).contains(nextRep.getId());
    }

    @Test
    @DisplayName("[E] 같은 track 이어도 평가 권한 없는 MASTER·HOST 미작성자는 풀에서 제외")
    void masterAndHostWithoutPermissionExcluded() {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin engStaff = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        Admin master = saveAdmin(Admin.Role.MASTER, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        Admin host = saveAdmin(Admin.Role.HOST, Admin.TeamName.그룹리더, Track.ENGINEERING);
        assertThat(effectivePermissions.of(master)).doesNotContain(
                Permission.EVALUATION_OWN_TRACK_WRITE, Permission.EVALUATION_ALL_TRACK_WRITE);
        assertThat(effectivePermissions.of(host)).doesNotContain(
                Permission.EVALUATION_OWN_TRACK_WRITE, Permission.EVALUATION_ALL_TRACK_WRITE);

        assertThat(evaluatorIds(eng.getId(), engStaff))
                .contains(engStaff.getId())
                .doesNotContain(master.getId(), host.getId());
        assertThat(interviewerIds(eng.getId(), engStaff))
                .contains(engStaff.getId())
                .doesNotContain(master.getId(), host.getId());
    }

    @Test
    @DisplayName("[ALL-only] 차기대표진에서 OWN REVOKE → ALL 만 남아도 같은 track 평가 저장·목록(전 track)·평가자 풀 포함")
    void allTrackOnlyViaOwnRevoke() {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Applicant ana = saveApplicant(r, Track.ANALYSIS, Applicant.ApplicantStatus.SUBMITTED);
        Admin nextRep = saveAdmin(Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
        Admin engStaff = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        override(nextRep, Permission.EVALUATION_OWN_TRACK_WRITE, AdminPermissionOverride.Effect.REVOKE);
        Set<Permission> perms = effectivePermissions.of(nextRep);
        assertThat(perms).contains(Permission.EVALUATION_ALL_TRACK_WRITE)
                .doesNotContain(Permission.EVALUATION_OWN_TRACK_WRITE);

        // 평가자 풀 — 미작성 상태에서도 같은 track 지원자의 현재 평가 가능자로 포함
        assertThat(evaluatorIds(eng.getId(), engStaff)).contains(nextRep.getId());

        // 목록 — 본인 track·타 track 모두
        assertThat(recruitmentService.getApplicants(r.getId(), nextRep, perms))
                .extracting(x -> x.getId()).contains(eng.getId(), ana.getId());

        // 단건 — 같은 track 지원자 평가 저장 성공
        MyEvaluationResponse saved = recruitmentService.saveMyEvaluation(
                eng.getId(), saveReq(EvaluationDecision.PASS, 8, "ALL-only 평가"), nextRep, perms);
        assertThat(saved.getMemo()).isEqualTo("ALL-only 평가");
    }

    @Test
    @DisplayName("[soft delete] 평가 작성 후 삭제된 평가자 — 집계·상세·면접 질문 모두 과거 기록 보존, 삭제된 미작성자는 제외")
    void softDeletedAuthorKeptInAggregationAndDetail() {
        Recruitment r = saveRecruitment(27);
        Applicant eng = saveApplicant(r, Track.ENGINEERING, Applicant.ApplicantStatus.SUBMITTED);
        Admin a = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        Admin b = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
        Admin unwritten = saveAdmin(Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

        recruitmentService.saveMyEvaluation(eng.getId(),
                saveReq(EvaluationDecision.PASS, 9, "A 평가", "A 질문"), a, effectivePermissions.of(a));
        recruitmentService.saveMyEvaluation(eng.getId(),
                saveReq(EvaluationDecision.HOLD, 5, "B 평가"), b, effectivePermissions.of(b));
        // upsert 가 @Modifying(clearAutomatically = true) 라 위 Admin 객체는 detached 다 — 다시 읽어서 삭제하고
        // DB 에 반영한다 (실제로는 별도 요청의 AdminService 가 커밋한다)
        adminRepository.findById(a.getId()).orElseThrow().softDelete();
        adminRepository.findById(unwritten.getId()).orElseThrow().softDelete();
        adminRepository.flush();
        Set<Permission> viewerPerms = effectivePermissions.of(b);

        // 집계 — A + B
        ApplicantEvaluationResponse row = recruitmentService.getApplicantEvaluations(r.getId(), b, viewerPerms).stream()
                .filter(x -> x.getId().equals(eng.getId())).findFirst().orElseThrow();
        assertThat(row.getPassCount()).isEqualTo(1);
        assertThat(row.getHoldCount()).isEqualTo(1);
        assertThat(row.getTotalScore()).isEqualTo(14);

        // 상세 — 집계와 같은 A + B. 삭제된 미작성자는 새로 노출되지 않는다
        ApplicantEvaluatorsResponse res = recruitmentService.getApplicantEvaluators(eng.getId(), b, viewerPerms);
        assertThat(res.getEvaluations()).extracting(EvaluatorEvaluationResponse::getAdminId)
                .contains(a.getId(), b.getId())
                .doesNotContain(unwritten.getId());
        assertThat(res.getEvaluations()).filteredOn(e -> e.getAdminId().equals(a.getId()))
                .singleElement().satisfies(e -> {
                    assertThat(e.getDecision()).isEqualTo(EvaluationDecision.PASS);
                    assertThat(e.getMemo()).isEqualTo("A 평가");
                });

        // 면접 질문 — 같은 풀, A 의 과거 질문 보존
        ApplicantInterviewQuestionsResponse iq =
                recruitmentService.getApplicantInterviewQuestions(eng.getId(), b, viewerPerms);
        assertThat(iq.getInterviewQuestions()).extracting(EvaluatorInterviewQuestionResponse::getAdminId)
                .doesNotContain(unwritten.getId());
        assertThat(iq.getInterviewQuestions()).filteredOn(q -> q.getAdminId().equals(a.getId()))
                .singleElement()
                .satisfies(q -> assertThat(q.getInterviewQuestion()).isEqualTo("A 질문"));
    }
}
