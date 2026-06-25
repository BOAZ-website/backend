package com.boaz.backend.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.global.common.enums.Track;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

    // 공고에 연관된 지원자 존재 여부 확인
    boolean existsByRecruitmentId(Long recruitmentId);

    // recruitment_id 기반 전체 삭제
    void deleteByRecruitmentId(Long recruitmentId);

    // (recruitment_id, user_id) 조합으로 지원서 조회
    Optional<Applicant> findByRecruitmentIdAndUserId(Long recruitmentId, Long userId);

    // recruitment_id + track + status + (선택) final_decision 기반 조회 (CSV 다운로드용)
    // - JOIN FETCH a.user: user_id 접근 시 N+1 방지
    // - ORDER BY a.submittedAt ASC: 제출 순 정렬 (DRAFT 생성 시점과 분리)
    // - decision == null: 전체(필터 미적용), 값이 있으면 해당 final_decision 만 추출
    @Query("SELECT a FROM Applicant a JOIN FETCH a.user " +
           "WHERE a.recruitment.id = :recruitmentId AND a.track = :track AND a.status = :status " +
           "AND (:decision IS NULL OR a.finalDecision = :decision) " +
           "ORDER BY a.submittedAt ASC")
    List<Applicant> findSubmittedByRecruitmentIdAndTrackAndDecision(
            @Param("recruitmentId") Long recruitmentId,
            @Param("track") Track track,
            @Param("status") Applicant.ApplicantStatus status,
            @Param("decision") EvaluationDecision decision
    );

    // userId + status 기반 조회 (합격자 승격용, recruitment JOIN FETCH로 term 접근)
    @Query("SELECT a FROM Applicant a JOIN FETCH a.recruitment WHERE a.user.id = :userId AND a.status = :status")
    Optional<Applicant> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") Applicant.ApplicantStatus status
    );

    // 지원자 대시보드용: 공고 내 전체 지원자 (DRAFT 포함, user JOIN FETCH로 N+1 방지)
    // - MySQL은 DESC 정렬 시 NULL을 뒤로 보내므로 DRAFT(submittedAt null)가 목록 끝에 위치
    @Query("SELECT a FROM Applicant a JOIN FETCH a.user " +
           "WHERE a.recruitment.id = :recruitmentId " +
           "ORDER BY a.submittedAt DESC")
    List<Applicant> findByRecruitmentIdWithUser(@Param("recruitmentId") Long recruitmentId);

    // 평가 대시보드용: 공고 내 status 지원자 (SUBMITTED, user JOIN FETCH)
    @Query("SELECT a FROM Applicant a JOIN FETCH a.user " +
           "WHERE a.recruitment.id = :recruitmentId AND a.status = :status " +
           "ORDER BY a.submittedAt DESC")
    List<Applicant> findByRecruitmentIdAndStatusWithUser(
            @Param("recruitmentId") Long recruitmentId,
            @Param("status") Applicant.ApplicantStatus status
    );
}
