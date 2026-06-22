package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.recruitment.dto.request.EvaluationSaveRequest;
import com.boaz.backend.domain.recruitment.dto.request.FinalDecisionUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.response.*;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantEval;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.repository.ApplicantEvalRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
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
import java.util.Optional;

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
    @Mock ApplicantEvalRepository applicantEvalRepository;
    @Mock AdminRepository adminRepository;
    @Spy  ObjectMapper objectMapper;

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

    // ── 1. 전체 지원서 조회 (지원자 대시보드) ──────────────

    @Nested
    @DisplayName("getApplicants")
    class GetApplicants {

        @Test
        @DisplayName("[정상] 공고 내 전체 지원자(DRAFT 포함) 반환")
        void success() {
            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdWithUser(1L)).willReturn(List.of(
                    applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING),
                    applicant(102L, Applicant.ApplicantStatus.DRAFT, null)
            ));

            List<ApplicantSummaryResponse> result = recruitmentService.getApplicants(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getStatus()).isEqualTo(Applicant.ApplicantStatus.SUBMITTED);
            assertThat(result.get(1).getStatus()).isEqualTo(Applicant.ApplicantStatus.DRAFT);
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 공고 → RECRUITMENT_NOT_FOUND")
        void recruitmentNotFound() {
            given(recruitmentRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> recruitmentService.getApplicants(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RECRUITMENT_NOT_FOUND);
            verify(applicantRepository, never()).findByRecruitmentIdWithUser(any());
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

            List<ApplicantEvaluationResponse> result = recruitmentService.getApplicantEvaluations(1L);

            ApplicantEvaluationResponse r1 = result.get(0);
            assertThat(r1.getPassCount()).isEqualTo(1);
            assertThat(r1.getHoldCount()).isEqualTo(1);
            assertThat(r1.getFailCount()).isEqualTo(0);
            assertThat(r1.getTotalScore()).isEqualTo(15);   // 9 + 6, null 제외
            assertThat(r1.getFinalDecision()).isEqualTo(EvaluationDecision.PENDING);

            ApplicantEvaluationResponse r2 = result.get(1);
            assertThat(r2.getFailCount()).isEqualTo(1);
            assertThat(r2.getTotalScore()).isEqualTo(3);
            assertThat(r2.getFinalDecision()).isEqualTo(EvaluationDecision.PASS);
        }

        @Test
        @DisplayName("[정상] 평가가 없는 지원자는 개수·총점 0")
        void noEval() {
            Applicant a1 = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ANALYSIS);

            given(recruitmentRepository.existsById(1L)).willReturn(true);
            given(applicantRepository.findByRecruitmentIdAndStatusWithUser(1L, Applicant.ApplicantStatus.SUBMITTED))
                    .willReturn(List.of(a1));
            given(applicantEvalRepository.findByRecruitmentId(1L)).willReturn(List.of());

            ApplicantEvaluationResponse r = recruitmentService.getApplicantEvaluations(1L).get(0);
            assertThat(r.getPassCount()).isZero();
            assertThat(r.getHoldCount()).isZero();
            assertThat(r.getFailCount()).isZero();
            assertThat(r.getTotalScore()).isZero();
        }
    }

    // ── 3. 최종 평가 수정 (대표진 전용) ───────────────────

    @Nested
    @DisplayName("updateFinalDecision")
    class UpdateFinalDecision {

        private FinalDecisionUpdateRequest req(EvaluationDecision d) {
            FinalDecisionUpdateRequest r = new FinalDecisionUpdateRequest();
            ReflectionTestUtils.setField(r, "finalDecision", d);
            return r;
        }

        @Test
        @DisplayName("[정상] 대표진(SUPER+대표진)이 최종 평가 변경")
        void success() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.대표진, Track.ENGINEERING);
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));

            FinalDecisionResponse res = recruitmentService.updateFinalDecision(101L, req(EvaluationDecision.PASS), rep);

            assertThat(res.getFinalDecision()).isEqualTo(EvaluationDecision.PASS);
            assertThat(a.getFinalDecision()).isEqualTo(EvaluationDecision.PASS);
        }

        @Test
        @DisplayName("[권한] 서비스운영팀 SUPER → ACCESS_DENIED (대표진 아님)")
        void notRepresentativeTeam() {
            Admin superNotRep = admin(1L, Admin.Role.SUPER, Admin.TeamName.서비스운영팀, Track.ENGINEERING);

            assertThatThrownBy(() -> recruitmentService.updateFinalDecision(101L, req(EvaluationDecision.PASS), superNotRep))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(applicantRepository, never()).findById(any());
        }

        @Test
        @DisplayName("[권한] TEAM 대표진 → ACCESS_DENIED (SUPER 아님)")
        void notRepresentativeRole() {
            Admin teamRep = admin(1L, Admin.Role.TEAM, Admin.TeamName.대표진, Track.ENGINEERING);

            assertThatThrownBy(() -> recruitmentService.updateFinalDecision(101L, req(EvaluationDecision.PASS), teamRep))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("[예외] DRAFT 지원서 → INVALID_INPUT_VALUE")
        void draftRejected() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.대표진, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.DRAFT, null)));

            assertThatThrownBy(() -> recruitmentService.updateFinalDecision(101L, req(EvaluationDecision.PASS), rep))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            Admin rep = admin(1L, Admin.Role.SUPER, Admin.TeamName.대표진, Track.ENGINEERING);
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.updateFinalDecision(999L, req(EvaluationDecision.PASS), rep))
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
            given(adminRepository.findByTrackAndDeletedAtIsNullOrderByNameAsc(Track.ENGINEERING))
                    .willReturn(List.of(ev1, ev2));
            given(applicantEvalRepository.findByApplicantIdWithAdmin(101L))
                    .willReturn(List.of(eval(1L, a, ev1, EvaluationDecision.PASS, 8, "ok")));

            ApplicantEvaluatorsResponse res = recruitmentService.getApplicantEvaluators(101L);

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
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getApplicantEvaluators(999L))
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

            MyEvaluationResponse res = recruitmentService.getMyEvaluation(101L, me);

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

            assertThat(recruitmentService.getMyEvaluation(101L, me)).isNull();
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.getMyEvaluation(999L, me))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }
    }

    // ── 6. 개인 평가 저장 (upsert) ────────────────────────

    @Nested
    @DisplayName("saveMyEvaluation")
    class SaveMyEvaluation {

        private EvaluationSaveRequest req(EvaluationDecision d, Integer score, String memo) {
            EvaluationSaveRequest r = new EvaluationSaveRequest();
            ReflectionTestUtils.setField(r, "decision", d);
            ReflectionTestUtils.setField(r, "score", score);
            ReflectionTestUtils.setField(r, "memo", memo);
            return r;
        }

        @Test
        @DisplayName("[정상] 본인 부문 지원자 → upsert 호출 후 저장값 반환")
        void success() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            Applicant a = applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING);
            given(applicantRepository.findById(101L)).willReturn(Optional.of(a));
            given(applicantEvalRepository.findByApplicantIdAndAdminId(101L, 1L))
                    .willReturn(Optional.of(eval(9L, a, me, EvaluationDecision.PASS, 10, "great")));

            MyEvaluationResponse res = recruitmentService.saveMyEvaluation(
                    101L, req(EvaluationDecision.PASS, 10, "great"), me);

            verify(applicantEvalRepository).upsert(101L, 1L, "PASS", 10, "great");
            assertThat(res.getDecision()).isEqualTo(EvaluationDecision.PASS);
            assertThat(res.getScore()).isEqualTo(10);
        }

        @Test
        @DisplayName("[권한] 타 부문 지원자 → ACCESS_DENIED, upsert 미호출")
        void trackMismatch() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ANALYSIS);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.SUBMITTED, Track.ENGINEERING)));

            assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(
                    101L, req(EvaluationDecision.PASS, 10, "x"), me))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(applicantEvalRepository, never()).upsert(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("[예외] DRAFT 지원서 → INVALID_INPUT_VALUE")
        void draftRejected() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(101L))
                    .willReturn(Optional.of(applicant(101L, Applicant.ApplicantStatus.DRAFT, null)));

            assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(
                    101L, req(EvaluationDecision.PASS, 10, "x"), me))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("[예외] 존재하지 않는 지원자 → APPLICATION_NOT_FOUND")
        void notFound() {
            Admin me = admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, Track.ENGINEERING);
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recruitmentService.saveMyEvaluation(
                    999L, req(EvaluationDecision.PASS, 10, "x"), me))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }
    }
}
