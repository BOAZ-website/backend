package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantAnswerRepository extends JpaRepository<ApplicantAnswer, Long> {
}