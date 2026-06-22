package com.boaz.backend.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.boaz.backend.domain.recruitment.entity.ApplicantEval;

public interface ApplicantEvalRepository extends JpaRepository<ApplicantEval, Long> {

    // 개인 평가 조회/저장(upsert) — (applicant_id, admin_id) 단건
    Optional<ApplicantEval> findByApplicantIdAndAdminId(Long applicantId, Long adminId);

    // 개인 평가 저장 — 원자적 upsert. find→insert TOCTOU(최초 저장 동시 호출 시 유니크 위반) 회피.
    // created_at/updated_at은 Auditing 우회이므로 직접 세팅, decision은 ENUM STRING(name) 바인딩.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO applicant_eval " +
            "(applicant_id, admin_id, decision, score, memo, created_at, updated_at) " +
            "VALUES (:applicantId, :adminId, :decision, :score, :memo, NOW(6), NOW(6)) " +
            "ON DUPLICATE KEY UPDATE " +
            "decision = VALUES(decision), score = VALUES(score), memo = VALUES(memo), updated_at = NOW(6)",
            nativeQuery = true)
    void upsert(@Param("applicantId") Long applicantId,
                @Param("adminId") Long adminId,
                @Param("decision") String decision,
                @Param("score") Integer score,
                @Param("memo") String memo);

    // 지원서별 평가 조회 — 한 지원자의 모든 평가 (평가자 정보 JOIN FETCH)
    @Query("SELECT e FROM ApplicantEval e JOIN FETCH e.admin " +
           "WHERE e.applicant.id = :applicantId")
    List<ApplicantEval> findByApplicantIdWithAdmin(@Param("applicantId") Long applicantId);

    // 평가 대시보드 집계용 — 공고 내 모든 평가 (Java에서 applicant 단위 집계)
    @Query("SELECT e FROM ApplicantEval e WHERE e.applicant.recruitment.id = :recruitmentId")
    List<ApplicantEval> findByRecruitmentId(@Param("recruitmentId") Long recruitmentId);
}
