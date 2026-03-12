package com.boaz.backend.domain.recruitment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.boaz.backend.domain.recruitment.entity.Applicant;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
}