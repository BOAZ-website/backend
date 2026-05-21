package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicantAnswerRepository extends JpaRepository<ApplicantAnswer, Long> {

    // 질문에 연관된 답변 존재 여부 확인
    boolean existsByQuestionId(Long questionId);

    // applicant_id 기반 조회
    @Query("SELECT aa FROM ApplicantAnswer aa WHERE aa.applicant.id IN :applicantIds")
    List<ApplicantAnswer> findByApplicantIds(@Param("applicantIds") List<Long> applicantIds);

    // 지원자 단건 답변 전체 삭제 (임시저장 덮어쓰기, 제출 시 DRAFT 답변 교체)
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ApplicantAnswer aa WHERE aa.applicant.id = :applicantId")
    void deleteByApplicantId(@Param("applicantId") Long applicantId);

    // recruitment 내 전체 답변 삭제 (서브쿼리로 메모리 적재 없이 벌크 삭제)
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ApplicantAnswer aa WHERE aa.applicant.id IN (SELECT a.id FROM Applicant a WHERE a.recruitment.id = :recruitmentId)")
    void deleteByRecruitmentId(@Param("recruitmentId") Long recruitmentId);
}