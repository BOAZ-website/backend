package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicantAnswerRepository extends JpaRepository<ApplicantAnswer, Long> {

    // applicant_id 기반 조회
    @Query("SELECT aa FROM ApplicantAnswer aa WHERE aa.applicant.id IN :applicantIds")
    List<ApplicantAnswer> findByApplicantIds(@Param("applicantIds") List<Long> applicantIds);
}