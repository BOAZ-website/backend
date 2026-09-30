package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.recruitment.dto.request.EvaluationSaveRequest;
import com.boaz.backend.domain.recruitment.dto.request.FinalDecisionUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.response.*;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;
import com.boaz.backend.domain.recruitment.entity.ApplicantEval;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.repository.ApplicantAnswerRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicantEvalRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.authz.EffectivePermissions;
import com.boaz.backend.global.security.authz.Permission;
import com.boaz.backend.global.security.authz.ScopeGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("지원서 평가 (어드민) 서비스 단위 테스트")
class RecruitmentEvaluationServiceTest {

    @InjectMocks
    RecruitmentService recruitmentService;

    @Mock RecruitmentRepository recruitmentRepository;
    @Mock ApplicantRepository applicantRepository;
    @Mock ApplicantAnswerRepository applicantAnswerRepository;
    @Mock ApplicantEvalRepository applicantEvalRepository;
    @Mock AdminRepository adminRepository;
    @Spy  ObjectMapper objectMapper;
    // 3층 판정을 실제로 태운다 — 부문 범위가 permission 으로 갈리는지를 이 테스트가 본다.
    @Spy  ScopeGuard scopeGuard;
    // 평가자 풀 계산용 — 여러 계정의 유효 권한을 한 번에 계산하는 of(Collection) 만 쓰여야 한다.
    @Mock EffectivePermissions effectivePermissions;

    // ── 주체의 유효 권한 (컨트롤러가 AdminUserDetails.getPermissions() 로 넘기는 자리) ──
    // role/teamName 이 아니라 이 집합이 판정을 가른다. 기대값은 최종 권한 매트릭스에서 옮겼다.
    private static final Set<Permission> OWN_TRACK = Set.of(Permission.EVALUATION_OWN_TRACK_WRITE);
    private static final Set<Permission> ALL_TRACK = Set.of(
            Permission.EVALUATION_OWN_TRACK_WRITE, Permission.EVALUATION_ALL_TRACK_WRITE);
    /** 대표진 — 본인 부문 평가 + 최종 합불 CUD. */
    private static final Set<Permission> REP = Set.of(
            Permission.EVALUATION_OWN_TRACK_WRITE, Permission.FINAL_DECISION_WRITE);
    /** 차기대표진 — 전 부문 평가 + 최종 합불 CUD. */
    private static final Set<Permission> NEXT_REP = Set.of(
            Permission.EVALUATION_OWN_TRACK_WRITE, Permission.EVALUATION_ALL_TRACK_WRITE,
            Permission.FINAL_DECISION_WRITE);

    // ── 헬퍼 ──────────────────────────────────────────

    private Admin admin(Long id, Admin.Role role, Admin.TeamName team, Track track) {
        Admin a = Admin.builder()
                .username("u" + id).password("p").role(role).name("admin" + id)
                .track(track).term(27).teamName(team).createdBy(null)
                .build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private User user(Long id) {
        User u = User.builder()
                .provider("kakao").providerId("pid" + id)
                .nickname("nick" + id).memberType(MemberType.OUTSIDER)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Applicant applicant(Long id, Applicant.ApplicantStatus status, Track track) {
        Applicant a = Applicant.builder()
                .recruitment(mock(Recruitment.class)).user(user(id)).status(status).track(track)
                .name("name" + id).email(id + "@example.com").phone("01000000000")
                .build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private ApplicantEval eval(Long id, Applicant applicant, Admin admin,
                               EvaluationDecision decision, Integer score, String memo) {
        ApplicantEval e = ApplicantEval.builder()
                .applicant(applicant).admin(admin).decision(decision).score(score).memo(memo)
                .build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private ApplicationQuestion question(Long id, Integer orderNum, ApplicationQuestion.Type type, String content) {
        ApplicationQuestion q = ApplicationQuestion.create(
                mock(Recruitment.class), "label" + id, ApplicationQuestion.Category.COMMON,
                type, content, null, 500, null, orderNum, true);
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private ApplicantAnswer answer(Applicant applicant, ApplicationQuestion question, String text, String json) {
        return ApplicantAnswer.builder()
                .applicant(applicant).question(question).answerText(text).answerJson(json)
                .build();
    }

    // ── 1. 전체 지원서 조회 (지원자 대시보드) ──────────────

    @Nested
    @DisplayName("getApplicants")
    class GetApplicants {

        @Test
        @DisplayName("[정상] 전 부문 평가 권한 보유(차기 대표진) → 전 부문(DRAFT 포함) 반환")
        void nextRepresentativeSeesAll() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdWithUser(1L)).willReturn(List.of(
                    applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING),
                    applicant(102L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)
            ));

            List<ApplicantSummaryResponse> result = recruitmentService.getApplicants(1L, rep, ALL_TRACK);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("[범위] 본인 부문 평가 권한만 보유(현재 대표진) → 본인 track만 반환")
        void currentRepresentativeOwnTrackOnly() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.대표진, Track.ENGINEERING);
            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdWithUser(1L)).willReturn(List.of(
                    applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING),
                    applicant(102L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS),
                    applicant(103L, Applicant.ApplicantStatus.DRAFT, null)
            ));

            List<ApplicantSummaryResponse> result = recruitmentService.getApplicants(1L, rep, OWN_TRACK);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTrack()).isEqualTo(Track.ENGINEERING);
        }

        @Test
        @DisplayName("[범위] 본인 부문 평가 권한만 보유(운영진) → 본인 track만 반환, track 없는 DRAFT 제외")
        void nonRepresentativeOwnTrackOnly() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdWithUser(1L)).willReturn(List.of(
                    applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING),
                    applicant(102L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS),
                    applicant(103L, Applicant.ApplicantStatus.DRAFT, null)
            ));

            List<ApplicantSummaryResponse> result = recruitmentService.getApplicants(1L, me, OWN_TRACK);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTrack()).isEqualTo(Track.ENGINEERING);
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 공고 → RECRUITMENT_NOT_FOUND")
        void recruitmentNotFound() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(recruitmentRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> recruitmentService.getApplicants(999L, me, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
            verify(applicantRepository, never()).findByRecruitmentIdWithUser(any());
        }

        @Test
        @DisplayName("[범위] role과 무관 — 차기대표진이라도 전 부문 평가 권한이 없으면 본인 track만 반환")
        void nextRepresentativeWithoutAllTrackOwnOnly() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdWithUser(1L)).willReturn(List.of(
                    applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING),
                    applicant(102L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)
            ));

            List<ApplicantSummaryResponse> result = recruitmentService.getApplicants(1L, rep, OWN_TRACK);

            assertThat(result).extracting(ApplicantSummaryResponse::getTrack).containsExactly(Track.ENGINEERING);
        }

        @Test
        @DisplayName("[정상] role과 무관 — 전 부문 평가 권한을 부여받은 운영진은 전 부문(track 없는 DRAFT 포함) 반환")
        void grantedAllTrackSeesAll() {
            Admin granted = admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀, Track.ENGINEERING);
            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdWithUser(1L)).willReturn(List.of(
                    applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING),
                    applicant(102L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS),
                    applicant(103L, Applicant.ApplicantStatus.DRAFT, null)
            ));

            List<ApplicantSummaryResponse> result = recruitmentService.getApplicants(1L, granted, ALL_TRACK);

            assertThat(result).hasSize(3);
        }
    }

    // ── 2. 전체 지원서 및 평가 조회 (평가 대시보드, 서버 집계) ──

    @Nested
    @DisplayName("getApplicantEvaluations")
    class GetApplicantEvaluations {

        @Test
        @DisplayName("[정상] PASS/HOLD/FAIL 개수 + 총점(null·PENDING 제외) 집계, final_decision 반영")
        void aggregate() {
            Admin ev1 = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Admin ev2 = admin(2L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

            Applicant a1 = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            a1.updateFinalDecision(EvaluationDecision.PENDING);
            Applicant a2 = applicant(102L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            a2.updateFinalDecision(EvaluationDecision.PASS);

            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdAndStatusWithUser(1L, Applicant.ApplicantStatus.SUBMITTED))
                    .willReturn(List.of(a1, a2));
            given(applicantEvalRepository.findByRecruitmentId(1L)).willReturn(List.of(
                    // a1: PASS(9), HOLD(6), PENDING(null) → pass1 hold1 fail0 total15
                    eval(1L, a1, ev1, EvaluationDecision.PASS, 9, "good"),
                    eval(2L, a1, ev2, EvaluationDecision.HOLD, 6, "maybe"),
                    eval(3L, a1, admin(3L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING),
                            EvaluationDecision.PENDING, null, null),
                    // a2: FAIL(3) → fail1 total3
                    eval(4L, a2, ev1, EvaluationDecision.FAIL, 3, "no")
            ));

            // 로그인 본인(ev1)으로 조회 → my_decision은 ev1의 결정
            List<ApplicantEvaluationResponse> result = recruitmentService.getApplicantEvaluations(1L, ev1, OWN_TRACK);

            ApplicantEvaluationResponse r1 = result.get(0);
            assertThat(r1.getPassCount()).isEqualTo(1);
            assertThat(r1.getHoldCount()).isEqualTo(1);
            assertThat(r1.getFailCount()).isEqualTo(0);
            assertThat(r1.getTotalScore()).isEqualTo(15);   // 9 + 6, null 제외
            assertThat(r1.getFinalDecision()).isEqualTo(EvaluationDecision.PENDING);
            assertThat(r1.getMyDecision()).isEqualTo(EvaluationDecision.PASS);   // ev1의 a1 평가

            ApplicantEvaluationResponse r2 = result.get(1);
            assertThat(r2.getFailCount()).isEqualTo(1);
            assertThat(r2.getTotalScore()).isEqualTo(3);
            assertThat(r2.getFinalDecision()).isEqualTo(EvaluationDecision.PASS);
            assertThat(r2.getMyDecision()).isEqualTo(EvaluationDecision.FAIL);   // ev1의 a2 평가
        }

        @Test
        @DisplayName("[정상] 평가가 없는 지원자는 개수·총점 0")
        void noEval() {
            Applicant a1 = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS);

            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdAndStatusWithUser(1L, Applicant.ApplicantStatus.SUBMITTED))
                    .willReturn(List.of(a1));
            given(applicantEvalRepository.findByRecruitmentId(1L)).willReturn(List.of());

            Admin rep = admin(9L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ANALYSIS);
            ApplicantEvaluationResponse r = recruitmentService.getApplicantEvaluations(1L, rep, ALL_TRACK).get(0);
            assertThat(r.getPassCount()).isZero();
            assertThat(r.getHoldCount()).isZero();
            assertThat(r.getFailCount()).isZero();
            assertThat(r.getTotalScore()).isZero();
            assertThat(r.getMyDecision()).isNull();   // 본인 미평가 → null
        }

        @Test
        @DisplayName("[범위] 본인 부문 평가 권한만 보유 → 타 부문 지원서 제외 / 전 부문 권한 보유 → 전부 포함")
        void trackFilter() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.운영지원팀, Track.ENGINEERING);
            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdAndStatusWithUser(1L, Applicant.ApplicantStatus.SUBMITTED))
                    .willReturn(List.of(
                            applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING),
                            applicant(102L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)));
            given(applicantEvalRepository.findByRecruitmentId(1L)).willReturn(List.of());

            assertThat(recruitmentService.getApplicantEvaluations(1L, me, OWN_TRACK))
                    .extracting(ApplicantEvaluationResponse::getId).containsExactly(101L);
            assertThat(recruitmentService.getApplicantEvaluations(1L, me, ALL_TRACK))
                    .extracting(ApplicantEvaluationResponse::getId).containsExactly(101L, 102L);
        }
    }

    // ── 3. 최종 평가 수정 (FINAL_DECISION_WRITE 독립 permission, 부문 범위 없음) ──
    // FINAL_DECISION_WRITE 판정은 2층(@PreAuthorize)이 한다 — ApplicantEvaluationPermissionGridTest 가 본다.
    // 서비스는 주체·권한을 받지 않으며, 서류 평가용 부문 범위(checkTrack)를 적용하지 않는다.

    @Nested
    @DisplayName("updateFinalDecision")
    class UpdateFinalDecision {

        private FinalDecisionUpdateRequest req(EvaluationDecision d) {
            FinalDecisionUpdateRequest r = new FinalDecisionUpdateRequest();
            ReflectionTestUtils.setField(r, "finalDecision", d);
            return r;
        }

        @Test
        @DisplayName("[정상] 같은 track 지원자 → 최종 평가 변경, 부문 판정(checkTrack) 없음")
        void ownTrack() {
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));

            FinalDecisionResponse res = recruitmentService.updateFinalDecision(101L, req(EvaluationDecision.PASS));

            assertThat(res.getFinalDecision()).isEqualTo(EvaluationDecision.PASS);
            assertThat(a.getFinalDecision()).isEqualTo(EvaluationDecision.PASS);
            verify(scopeGuard, never()).checkTrack(any(), any(), any());
        }

        @Test
        @DisplayName("[정상] 타 track 지원자 → 최종 평가 변경 (전 부문 평가 권한과 무관), checkTrack 없음")
        void crossTrack() {
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));

            FinalDecisionResponse res = recruitmentService.updateFinalDecision(101L, req(EvaluationDecision.FAIL));

            assertThat(res.getFinalDecision()).isEqualTo(EvaluationDecision.FAIL);
            assertThat(a.getFinalDecision()).isEqualTo(EvaluationDecision.FAIL);
            verify(scopeGuard, never()).checkTrack(any(), any(), any());
        }

        @Test
        @DisplayName("[예외] DRAFT 지원서 → INVALID_INPUT_VALUE")
        void draftRejected() {
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.DRAFT, null)));

            assertThatThrownBy(() -> recruitmentService.updateFinalDecision(101L, req(EvaluationDecision.PASS)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.updateFinalDecision(999L, req(EvaluationDecision.PASS)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }
    }

    // ── 4. 지원서별 평가 조회 ─────────────────────────────

    @Nested
    @DisplayName("getApplicantEvaluators")
    class GetApplicantEvaluators {

        @Test
        @DisplayName("[정상] 부문 평가자 전체 반환, 미평가자는 decision/score/memo null")
        void mergeWithUnevaluated() {
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            Admin ev1 = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Admin ev2 = admin(2L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).willReturn(List.of(ev1, ev2));
            given(effectivePermissions.of(anyCollection())).willReturn(Map.of(1L, OWN_TRACK, 2L, OWN_TRACK));
            given(applicantEvalRepository.findByApplicantIdWithAdmin(101L))
                    .willReturn(List.of(eval(1L, a, ev1, EvaluationDecision.PASS, 8, "ok")));

            Admin viewer = admin(5L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            ApplicantEvaluatorsResponse res = recruitmentService.getApplicantEvaluators(101L, viewer, OWN_TRACK);

            assertThat(res.getApplicantId()).isEqualTo(101L);
            assertThat(res.getEvaluations()).hasSize(2);
            EvaluatorEvaluationResponse e1 = res.getEvaluations().get(0);
            assertThat(e1.getDecision()).isEqualTo(EvaluationDecision.PASS);
            assertThat(e1.getScore()).isEqualTo(8);
            EvaluatorEvaluationResponse e2 = res.getEvaluations().get(1); // 미평가
            assertThat(e2.getDecision()).isNull();
            assertThat(e2.getScore()).isNull();
            assertThat(e2.getMemo()).isNull();
        }

        @Test
        @DisplayName("[범위] 본인 부문 평가 권한만 보유 + 타 부문 지원자 → ACCESS_DENIED, 평가자 풀 조회 안 함")
        void currentRepresentativeCrossTrackDenied() {
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS);
            Admin rep = admin(5L, Admin.Role.SUPER, Admin.TeamName.대표진, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));

            assertThatThrownBy(() -> recruitmentService.getApplicantEvaluators(101L, rep, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findAllByDeletedAtIsNullOrderByCreatedAtAsc();
            verifyNoInteractions(effectivePermissions);
        }

        @Test
        @DisplayName("[정상] 전 부문 평가 권한 보유 + 타 부문 지원자 → 조회 가능")
        void nextRepresentativeCrossTrack() {
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS);
            Admin rep = admin(9L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).willReturn(List.of());
            given(effectivePermissions.of(anyCollection())).willReturn(Map.of());
            given(applicantEvalRepository.findByApplicantIdWithAdmin(101L)).willReturn(List.of());

            ApplicantEvaluatorsResponse res = recruitmentService.getApplicantEvaluators(101L, rep, ALL_TRACK);

            assertThat(res.getApplicantId()).isEqualTo(101L);
            assertThat(res.getEvaluations()).isEmpty();
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            Admin viewer = admin(5L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getApplicantEvaluators(999L, viewer, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }
    }

    // ── 5. 개인 평가 조회 ─────────────────────────────────

    @Nested
    @DisplayName("getMyEvaluation")
    class GetMyEvaluation {

        @Test
        @DisplayName("[정상] 본인 평가 존재 → 단건 반환")
        void found() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(applicantEvalRepository.findByApplicantIdAndAdminId(101L, 1L))
                    .willReturn(Optional.of(eval(7L, a, me, EvaluationDecision.HOLD, 5, "hmm")));

            MyEvaluationResponse res = recruitmentService.getMyEvaluation(101L, me, OWN_TRACK);

            assertThat(res).isNotNull();
            assertThat(res.getEvaluationId()).isEqualTo(7L);
            assertThat(res.getDecision()).isEqualTo(EvaluationDecision.HOLD);
        }

        @Test
        @DisplayName("[정상] 본인 평가 없음 → null")
        void none() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING)));
            given(applicantEvalRepository.findByApplicantIdAndAdminId(101L, 1L)).willReturn(Optional.empty());

            assertThat(recruitmentService.getMyEvaluation(101L, me, OWN_TRACK)).isNull();
        }

        @Test
        @DisplayName("[범위] 본인 부문 평가 권한만 보유 + 타 부문 지원자 개인 평가 조회 → ACCESS_DENIED")
        void crossTrackDenied() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)));

            assertThatThrownBy(() -> recruitmentService.getMyEvaluation(101L, me, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(applicantEvalRepository, never()).findByApplicantIdAndAdminId(any(), any());
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getMyEvaluation(999L, me, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getMyEvaluation — 전 부문 권한")
    class GetMyEvaluationAllTrack {

        @Test
        @DisplayName("[정상] 전 부문 평가 권한 보유 + 타 부문 지원자 → 개인 평가 조회 가능")
        void allTrackCrossTrack() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)));
            given(applicantEvalRepository.findByApplicantIdAndAdminId(101L, 1L)).willReturn(Optional.empty());

            assertThat(recruitmentService.getMyEvaluation(101L, rep, ALL_TRACK)).isNull();
            verify(applicantEvalRepository).findByApplicantIdAndAdminId(101L, 1L);
        }
    }

    // ── 6. 개인 평가 저장 (upsert) ────────────────────────

    @Nested
    @DisplayName("saveMyEvaluation")
    class SaveMyEvaluation {

        private EvaluationSaveRequest req(EvaluationDecision d, Integer score, String memo, String interviewQuestion) {
            EvaluationSaveRequest r = new EvaluationSaveRequest();
            ReflectionTestUtils.setField(r, "decision", d);
            ReflectionTestUtils.setField(r, "score", score);
            ReflectionTestUtils.setField(r, "memo", memo);
            ReflectionTestUtils.setField(r, "interviewQuestion", interviewQuestion);
            return r;
        }

        @Test
        @DisplayName("[정상] 본인 부문 지원자 → upsert(면접질문 포함) 호출 후 저장값 반환")
        void success() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            ApplicantEval saved = ApplicantEval.builder()
                    .applicant(a).admin(me).decision(EvaluationDecision.PASS).score(10).memo("great")
                    .interviewQuestion("면접 질문?").build();
            ReflectionTestUtils.setField(saved, "id", 9L);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(applicantEvalRepository.findByApplicantIdAndAdminId(101L, 1L)).willReturn(Optional.of(saved));

            MyEvaluationResponse res = recruitmentService.saveMyEvaluation(
                    101L, req(EvaluationDecision.PASS, 10, "great", "면접 질문?"), me, OWN_TRACK);

            verify(applicantEvalRepository).upsert(101L, 1L, "PASS", 10, "great", "면접 질문?");
            assertThat(res.getDecision()).isEqualTo(EvaluationDecision.PASS);
            assertThat(res.getScore()).isEqualTo(10);
            assertThat(res.getInterviewQuestion()).isEqualTo("면접 질문?");
        }

        @Test
        @DisplayName("[정상] 전 부문 평가 권한 보유 + 타 부문 지원자 → 평가 가능")
        void nextRepresentativeCrossTrack() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(applicantEvalRepository.findByApplicantIdAndAdminId(101L, 1L))
                    .willReturn(Optional.of(eval(9L, a, rep, EvaluationDecision.PASS, 8, "ok")));

            MyEvaluationResponse res = recruitmentService.saveMyEvaluation(
                    101L, req(EvaluationDecision.PASS, 8, "ok", null), rep, ALL_TRACK);

            verify(applicantEvalRepository).upsert(101L, 1L, "PASS", 8, "ok", null);
            assertThat(res.getDecision()).isEqualTo(EvaluationDecision.PASS);
        }

        @Test
        @DisplayName("[범위] 본인 부문 평가 권한만 보유 + 타 부문 지원자 → ACCESS_DENIED, upsert 미호출")
        void trackMismatch() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING)));

            assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(
                    101L, req(EvaluationDecision.PASS, 10, "x", "q"), me, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(applicantEvalRepository, never()).upsert(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("[예외] DRAFT 지원서 → INVALID_INPUT_VALUE")
        void draftRejected() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.DRAFT, null)));

            assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(
                    101L, req(EvaluationDecision.PASS, 10, "x", "q"), me, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(
                    999L, req(EvaluationDecision.PASS, 10, "x", "q"), me, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("saveMyEvaluation — role과 무관한 부문 판정")
    class SaveMyEvaluationRoleIndependent {

        private EvaluationSaveRequest req() {
            EvaluationSaveRequest r = new EvaluationSaveRequest();
            ReflectionTestUtils.setField(r, "decision", EvaluationDecision.PASS);
            ReflectionTestUtils.setField(r, "score", 7);
            return r;
        }

        @Test
        @DisplayName("[범위] 차기대표진이라도 전 부문 평가 권한이 없으면 타 부문 → ACCESS_DENIED, upsert 미호출")
        void nextRepresentativeWithoutAllTrackDenied() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)));

            assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(101L, req(), rep, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(applicantEvalRepository, never()).upsert(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("[정상] 전 부문 평가 권한을 부여받은 운영진은 타 부문 지원자 평가 가능")
        void grantedAllTrackCrossTrack() {
            Admin granted = admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀, Track.ENGINEERING);
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(applicantEvalRepository.findByApplicantIdAndAdminId(101L, 1L))
                    .willReturn(Optional.of(eval(9L, a, granted, EvaluationDecision.PASS, 7, null)));

            recruitmentService.saveMyEvaluation(101L, req(), granted, ALL_TRACK);

            verify(applicantEvalRepository).upsert(101L, 1L, "PASS", 7, null, null);
        }

        @Test
        @DisplayName("[예외] DRAFT 지원서는 부문 판정 전에 INVALID_INPUT_VALUE")
        void draftBeforeScope() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.DRAFT, null)));

            assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(101L, req(), me, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            verify(scopeGuard, never()).checkTrack(any(), any(), any());
        }
    }

    // ── 7. 지원서 답변 조회 ───────────────────────────────

    @Nested
    @DisplayName("getApplicantAnswers")
    class GetApplicantAnswers {

        @Test
        @DisplayName("[정상] 문항 정보 + 답변(TEXT/TABLE) 순서대로 반환, {Track} 치환")
        void success() {
            Admin viewer = admin(5L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            ApplicationQuestion q1 = question(1L, 1, ApplicationQuestion.Type.TEXT, "{Track} 지원 동기를 작성해주세요.");
            ApplicationQuestion q2 = question(2L, 2, ApplicationQuestion.Type.TABLE, "기술 스택 숙련도");

            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(applicantAnswerRepository.findByApplicantIdWithQuestion(101L)).willReturn(List.of(
                    answer(a, q1, "저는 ~~", null),
                    answer(a, q2, null, "{\"Python\":\"능숙\"}")
            ));

            ApplicantAnswersResponse res = recruitmentService.getApplicantAnswers(101L, viewer, OWN_TRACK);

            assertThat(res.getApplicantId()).isEqualTo(101L);
            assertThat(res.getAnswers()).hasSize(2);

            ApplicantAnswersResponse.AnswerDetailResponse first = res.getAnswers().get(0);
            assertThat(first.getQuestionId()).isEqualTo(1L);
            assertThat(first.getType()).isEqualTo(ApplicationQuestion.Type.TEXT);
            assertThat(first.getContent()).isEqualTo("엔지니어링 지원 동기를 작성해주세요."); // {Track} 치환
            assertThat(first.getAnswer().asText()).isEqualTo("저는 ~~");

            ApplicantAnswersResponse.AnswerDetailResponse second = res.getAnswers().get(1);
            assertThat(second.getType()).isEqualTo(ApplicationQuestion.Type.TABLE);
            assertThat(second.getAnswer().get("Python").asText()).isEqualTo("능숙");
        }

        @Test
        @DisplayName("[정상] 답변 없음 → 빈 배열")
        void empty() {
            Admin viewer = admin(5L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)));
            given(applicantAnswerRepository.findByApplicantIdWithQuestion(101L)).willReturn(List.of());

            ApplicantAnswersResponse res = recruitmentService.getApplicantAnswers(101L, viewer, OWN_TRACK);

            assertThat(res.getApplicantId()).isEqualTo(101L);
            assertThat(res.getAnswers()).isEmpty();
        }

        @Test
        @DisplayName("[범위] 본인 부문 평가 권한만 보유 + 타 부문 지원서 답변 조회 → ACCESS_DENIED")
        void crossTrackDenied() {
            Admin viewer = admin(5L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)));

            assertThatThrownBy(() -> recruitmentService.getApplicantAnswers(101L, viewer, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(applicantAnswerRepository, never()).findByApplicantIdWithQuestion(any());
        }

        @Test
        @DisplayName("[정상] 전 부문 평가 권한 보유 + 타 부문 지원서 답변 → 조회 가능")
        void nextRepresentativeCrossTrack() {
            Admin rep = admin(9L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)));
            given(applicantAnswerRepository.findByApplicantIdWithQuestion(101L)).willReturn(List.of());

            ApplicantAnswersResponse res = recruitmentService.getApplicantAnswers(101L, rep, ALL_TRACK);

            assertThat(res.getApplicantId()).isEqualTo(101L);
            assertThat(res.getAnswers()).isEmpty();
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            Admin viewer = admin(5L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getApplicantAnswers(999L, viewer, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
            verify(applicantAnswerRepository, never()).findByApplicantIdWithQuestion(any());
        }
    }

    @Nested
    @DisplayName("getApplicantAnswers — track 없는 DRAFT 경계")
    class GetApplicantAnswersNullTrack {

        @Test
        @DisplayName("[범위] 본인 부문 평가 권한만 보유 + track 없는 DRAFT → ACCESS_DENIED")
        void ownTrackNullTrackDenied() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.DRAFT, null)));

            assertThatThrownBy(() -> recruitmentService.getApplicantAnswers(101L, me, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(applicantAnswerRepository, never()).findByApplicantIdWithQuestion(any());
        }

        @Test
        @DisplayName("[정상] 전 부문 평가 권한 보유 + track 없는 DRAFT → 조회 가능")
        void allTrackNullTrackAllowed() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.DRAFT, null)));
            given(applicantAnswerRepository.findByApplicantIdWithQuestion(101L)).willReturn(List.of());

            assertThat(recruitmentService.getApplicantAnswers(101L, rep, ALL_TRACK).getAnswers()).isEmpty();
        }
    }

    // ── 8. 지원서별 면접 질문 조회 ────────────────────────

    @Nested
    @DisplayName("getApplicantInterviewQuestions")
    class GetApplicantInterviewQuestions {

        private ApplicantEval evalWithQuestion(Applicant a, Admin admin, String interviewQuestion) {
            return ApplicantEval.builder()
                    .applicant(a).admin(admin).decision(EvaluationDecision.PASS).score(8).memo("m")
                    .interviewQuestion(interviewQuestion).build();
        }

        @Test
        @DisplayName("[정상] 부문 평가자 전체 반환, 미작성자는 interview_question null")
        void mergeWithUnwritten() {
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            Admin ev1 = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Admin ev2 = admin(2L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Admin viewer = admin(5L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).willReturn(List.of(ev1, ev2));
            given(effectivePermissions.of(anyCollection())).willReturn(Map.of(1L, OWN_TRACK, 2L, OWN_TRACK));
            given(applicantEvalRepository.findByApplicantIdWithAdmin(101L))
                    .willReturn(List.of(evalWithQuestion(a, ev1, "프로젝트 X에 대해 설명해주세요")));

            ApplicantInterviewQuestionsResponse res = recruitmentService.getApplicantInterviewQuestions(101L, viewer, OWN_TRACK);

            assertThat(res.getApplicantId()).isEqualTo(101L);
            assertThat(res.getInterviewQuestions()).hasSize(2);
            EvaluatorInterviewQuestionResponse q1 = res.getInterviewQuestions().get(0);
            assertThat(q1.getAdminId()).isEqualTo(1L);
            assertThat(q1.getTrack()).isEqualTo(Track.ENGINEERING);
            assertThat(q1.getInterviewQuestion()).isEqualTo("프로젝트 X에 대해 설명해주세요");
            EvaluatorInterviewQuestionResponse q2 = res.getInterviewQuestions().get(1); // 미작성
            assertThat(q2.getInterviewQuestion()).isNull();
        }

        @Test
        @DisplayName("[범위] 본인 부문 평가 권한만 보유 + 타 부문 지원자 → ACCESS_DENIED, 평가자 풀 조회 안 함")
        void crossTrackDenied() {
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS);
            Admin viewer = admin(5L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));

            assertThatThrownBy(() -> recruitmentService.getApplicantInterviewQuestions(101L, viewer, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findAllByDeletedAtIsNullOrderByCreatedAtAsc();
            verifyNoInteractions(effectivePermissions);
        }

        @Test
        @DisplayName("[정상] 전 부문 평가 권한 보유 + 타 부문 지원자 → 조회 가능")
        void nextRepresentativeCrossTrack() {
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS);
            Admin rep = admin(9L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).willReturn(List.of());
            given(effectivePermissions.of(anyCollection())).willReturn(Map.of());
            given(applicantEvalRepository.findByApplicantIdWithAdmin(101L)).willReturn(List.of());

            ApplicantInterviewQuestionsResponse res = recruitmentService.getApplicantInterviewQuestions(101L, rep, ALL_TRACK);

            assertThat(res.getApplicantId()).isEqualTo(101L);
            assertThat(res.getInterviewQuestions()).isEmpty();
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            Admin viewer = admin(5L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getApplicantInterviewQuestions(999L, viewer, OWN_TRACK))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }
    }

    // ── 9. 평가자 풀 (getApplicantEvaluators / getApplicantInterviewQuestions 공통) ──
    // 풀 = (현재 이 지원자 부문을 평가할 수 있는 살아 있는 계정) ∪ (이 지원자에 평가를 남긴 살아 있는 계정).
    // role/teamName 이 아니라 EffectivePermissions.of(Collection) 결과가 판정을 가른다.

    @Nested
    @DisplayName("평가자 풀 — 유효 권한 ∪ 기존 작성자")
    class EvaluatorPool {

        private static final Set<Permission> NONE = Set.of();
        private static final Set<Permission> ALL_ONLY = Set.of(Permission.EVALUATION_ALL_TRACK_WRITE);

        private final Applicant applicant = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
        // 조회 주체 — 3층 통과용. 풀 계산에는 쓰이지 않는다 (live 목록에 넣지 않음)
        private final Admin viewer = admin(900L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ANALYSIS);

        private Admin named(Admin a, String name) {
            ReflectionTestUtils.setField(a, "name", name);
            return a;
        }

        private void stub(List<Admin> liveAdmins, Map<Long, Set<Permission>> permissions, List<ApplicantEval> evals) {
            given(applicantRepository.findById(101L)).willReturn(Optional.of(applicant));
            given(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).willReturn(liveAdmins);
            given(effectivePermissions.of(anyCollection())).willReturn(permissions);
            given(applicantEvalRepository.findByApplicantIdWithAdmin(101L)).willReturn(evals);
        }

        /** 두 조회가 같은 풀을 같은 순서로 돌려주는지 확인하고, 그 adminId 목록을 반환한다. */
        private List<Long> poolIds() {
            List<Long> evaluators = recruitmentService.getApplicantEvaluators(101L, viewer, ALL_TRACK)
                    .getEvaluations().stream().map(EvaluatorEvaluationResponse::getAdminId).toList();
            List<Long> interviewers = recruitmentService.getApplicantInterviewQuestions(101L, viewer, ALL_TRACK)
                    .getInterviewQuestions().stream().map(EvaluatorInterviewQuestionResponse::getAdminId).toList();
            assertThat(interviewers).as("면접 질문 조회도 같은 풀 정책").isEqualTo(evaluators);
            return evaluators;
        }

        @Test
        @DisplayName("[포함] 본인 부문 평가 권한 + 같은 track")
        void ownSameTrackIncluded() {
            Admin own = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            stub(List.of(own), Map.of(1L, OWN_TRACK), List.of());

            assertThat(poolIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("[제외] 본인 부문 평가 권한 + 다른 track + 미작성")
        void ownOtherTrackExcluded() {
            Admin own = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
            stub(List.of(own), Map.of(1L, OWN_TRACK), List.of());

            assertThat(poolIds()).isEmpty();
        }

        @Test
        @DisplayName("[포함] 전 부문 평가 권한 + 다른 track — role/teamName 무관 (커스텀 GRANT 운영진)")
        void allOtherTrackIncluded() {
            Admin custom = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
            stub(List.of(custom), Map.of(1L, ALL_ONLY), List.of());

            assertThat(poolIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("[포함] 전 부문 평가 권한만 보유(OWN 없음) + 같은 track + 미작성 — ALL 은 본인 부문을 포함한다")
        void allOnlySameTrackIncluded() {
            Admin allOnly = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            stub(List.of(allOnly), Map.of(1L, ALL_ONLY), List.of());

            assertThat(poolIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("[제외] 평가 권한 없음 + 미작성")
        void noPermissionUnwrittenExcluded() {
            Admin none = admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀, Track.ENGINEERING);
            stub(List.of(none), Map.of(1L, NONE), List.of());

            assertThat(poolIds()).isEmpty();
        }

        @Test
        @DisplayName("[포함] 평가 권한 없음 + 기존 평가 작성 — 권한이 회수돼도 기존 평가는 사라지지 않는다")
        void noPermissionButAuthorIncluded() {
            Admin author = admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀, Track.ANALYSIS);
            stub(List.of(author), Map.of(1L, NONE),
                    List.of(eval(1L, applicant, author, EvaluationDecision.PASS, 7, "kept")));

            ApplicantEvaluatorsResponse res = recruitmentService.getApplicantEvaluators(101L, viewer, ALL_TRACK);
            assertThat(res.getEvaluations()).singleElement().satisfies(e -> {
                assertThat(e.getAdminId()).isEqualTo(1L);
                assertThat(e.getDecision()).isEqualTo(EvaluationDecision.PASS);
                assertThat(e.getMemo()).isEqualTo("kept");
            });
            assertThat(poolIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("[중복 제거] 현재 평가 가능자이면서 기존 작성자 → adminId 기준 1명")
        void evaluatorAndAuthorDeduplicated() {
            Admin both = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            stub(List.of(both), Map.of(1L, OWN_TRACK),
                    List.of(eval(1L, applicant, both, EvaluationDecision.HOLD, 5, "m")));

            assertThat(poolIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("[제외] MASTER · 같은 track · 평가 권한 없음 · 미작성")
        void masterWithoutPermissionExcluded() {
            Admin master = admin(1L, Admin.Role.MASTER, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            stub(List.of(master), Map.of(1L, NONE), List.of());

            assertThat(poolIds()).isEmpty();
        }

        @Test
        @DisplayName("[제외] HOST · 같은 track · 평가 권한 없음 · 미작성")
        void hostWithoutPermissionExcluded() {
            Admin host = admin(1L, Admin.Role.HOST, Admin.TeamName.그룹리더, Track.ENGINEERING);
            stub(List.of(host), Map.of(1L, NONE), List.of());

            assertThat(poolIds()).isEmpty();
        }

        @Test
        @DisplayName("[제외] 기본값은 본인 부문 평가지만 OWN REVOKE 된 계정 · 같은 track · 미작성")
        void ownRevokedExcluded() {
            // 서비스운영팀 TEAM 의 기본 세트에는 EVALUATION_OWN_TRACK_WRITE 가 있다. REVOKE 반영 결과를 유효 권한으로 준다.
            Admin revoked = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            stub(List.of(revoked), Map.of(1L, Set.of(Permission.FINAL_DECISION_READ)), List.of());

            assertThat(poolIds()).isEmpty();
        }

        @Test
        @DisplayName("[포함] soft delete 된 기존 작성자 — 과거 평가 기록으로 보존, 평가 내용도 그대로")
        void softDeletedAuthorKept() {
            Admin deleted = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            deleted.softDelete();
            Admin live = admin(2L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            // live 조회에는 삭제 계정이 없다 — 평가 행을 통해서만 들어온다
            stub(List.of(live), Map.of(2L, OWN_TRACK),
                    List.of(eval(1L, applicant, deleted, EvaluationDecision.PASS, 9, "gone")));

            assertThat(poolIds()).containsExactly(1L, 2L);
            ApplicantEvaluatorsResponse res = recruitmentService.getApplicantEvaluators(101L, viewer, ALL_TRACK);
            assertThat(res.getEvaluations()).filteredOn(e -> e.getAdminId().equals(1L))
                    .singleElement().satisfies(e -> {
                        assertThat(e.getDecision()).isEqualTo(EvaluationDecision.PASS);
                        assertThat(e.getMemo()).isEqualTo("gone");
                    });
        }

        @Test
        @DisplayName("[제외] soft delete 된 미작성 계정 — 과거 평가 권한이 있었어도 live 조회에 없으면 새로 노출되지 않는다")
        void softDeletedUnwrittenExcluded() {
            Admin live = admin(2L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            // 삭제된 같은 track OWN 계정(id 1)은 live 조회에도, 평가 행에도 없다
            stub(List.of(live), Map.of(2L, OWN_TRACK), List.of());

            assertThat(poolIds()).containsExactly(2L);
            verify(effectivePermissions, atLeastOnce()).of(List.of(live));
        }

        @Test
        @DisplayName("[중복 제거] 같은 계정이 live 평가 가능자·과거 작성자 양쪽으로 들어와도 adminId 기준 1번")
        void authorInBothPathsOnce() {
            Admin both = admin(1L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ANALYSIS);
            stub(List.of(both), Map.of(1L, ALL_TRACK),
                    List.of(eval(1L, applicant, both, EvaluationDecision.PASS, 9, "m")));

            assertThat(poolIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("[정렬] live 조회 순서·작성자 합류와 무관하게 이름 오름차순")
        void sortedByName() {
            Admin c = named(admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING), "다");
            Admin a = named(admin(2L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS), "가");
            Admin b = named(admin(3L, Admin.Role.TEAM, Admin.TeamName.기획팀, Track.ANALYSIS), "나");
            // c: OWN 같은 track / a: ALL / b: 권한 없음 + 작성자
            stub(List.of(c, a, b), Map.of(1L, OWN_TRACK, 2L, ALL_ONLY, 3L, NONE),
                    List.of(eval(1L, applicant, b, EvaluationDecision.FAIL, 1, "m")));

            assertThat(poolIds()).containsExactly(2L, 3L, 1L);
        }

        @Test
        @DisplayName("[쿼리] 여러 계정 권한은 of(Collection) 1회로 계산 — of(Admin) 반복 호출 없음")
        void batchPermissionLookup() {
            Admin a1 = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Admin a2 = admin(2L, Admin.Role.TEAM, Admin.TeamName.운영지원팀, Track.ENGINEERING);
            Admin a3 = admin(3L, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ANALYSIS);
            stub(List.of(a1, a2, a3), Map.of(1L, OWN_TRACK, 2L, OWN_TRACK, 3L, ALL_TRACK), List.of());

            recruitmentService.getApplicantEvaluators(101L, viewer, ALL_TRACK);

            verify(effectivePermissions, times(1)).of(List.of(a1, a2, a3));
            verify(effectivePermissions, never()).of(any(Admin.class));
        }
    }

    // ── 10. 전 부문 평가 권한만 보유 (OWN 없음) — ALL 은 본인 부문을 포함한 전 부문 ──
    // 기본 세트에는 이런 주체가 없고 오버라이드(차기대표진 OWN REVOKE 등)로만 생긴다.

    @Nested
    @DisplayName("전 부문 평가 권한만 보유 (OWN 없음)")
    class AllTrackOnly {

        private final Set<Permission> ALL_ONLY = Set.of(Permission.EVALUATION_ALL_TRACK_WRITE);

        @Test
        @DisplayName("[정상] getApplicants — 본인 track·타 track 지원서 모두 반환")
        void getApplicantsSeesOwnAndOtherTracks() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdWithUser(1L)).willReturn(List.of(
                    applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING),
                    applicant(102L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)
            ));

            List<ApplicantSummaryResponse> result = recruitmentService.getApplicants(1L, me, ALL_ONLY);

            assertThat(result).extracting(ApplicantSummaryResponse::getTrack)
                    .containsExactly(Track.ENGINEERING, Track.ANALYSIS);
        }

        @Test
        @DisplayName("[정상] getApplicantEvaluations — 본인 track·타 track 지원서 모두 반환")
        void getApplicantEvaluationsSeesOwnAndOtherTracks() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdAndStatusWithUser(1L, Applicant.ApplicantStatus.SUBMITTED))
                    .willReturn(List.of(
                            applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING),
                            applicant(102L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS)));
            given(applicantEvalRepository.findByRecruitmentId(1L)).willReturn(List.of());

            assertThat(recruitmentService.getApplicantEvaluations(1L, me, ALL_ONLY))
                    .extracting(ApplicantEvaluationResponse::getId).containsExactly(101L, 102L);
        }

        @Test
        @DisplayName("[정상] 같은 track 지원자 개인 평가 저장 — upsert 호출")
        void saveMyEvaluationSameTrack() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(applicantEvalRepository.findByApplicantIdAndAdminId(101L, 1L))
                    .willReturn(Optional.of(eval(9L, a, me, EvaluationDecision.PASS, 7, "ok")));
            EvaluationSaveRequest req = new EvaluationSaveRequest();
            ReflectionTestUtils.setField(req, "decision", EvaluationDecision.PASS);
            ReflectionTestUtils.setField(req, "score", 7);
            ReflectionTestUtils.setField(req, "memo", "ok");

            MyEvaluationResponse res = recruitmentService.saveMyEvaluation(101L, req, me, ALL_ONLY);

            verify(applicantEvalRepository).upsert(101L, 1L, "PASS", 7, "ok", null);
            assertThat(res.getDecision()).isEqualTo(EvaluationDecision.PASS);
        }

        @Test
        @DisplayName("[정상] 같은 track 지원자 개인 평가 조회 — 접근 가능")
        void getMyEvaluationSameTrack() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(applicantEvalRepository.findByApplicantIdAndAdminId(101L, 1L))
                    .willReturn(Optional.of(eval(9L, a, me, EvaluationDecision.HOLD, 5, "m")));

            MyEvaluationResponse res = recruitmentService.getMyEvaluation(101L, me, ALL_ONLY);

            assertThat(res.getDecision()).isEqualTo(EvaluationDecision.HOLD);
        }
    }
}
