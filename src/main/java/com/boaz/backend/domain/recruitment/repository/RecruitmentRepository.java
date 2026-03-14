package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RecruitmentRepository extends JpaRepository<Recruitment, Long> {

    // 현재 모집 중인 공고 조회 (현재 시간이 start_date ~ end_date 사이)
    @Query("SELECT r FROM Recruitment r WHERE r.startDate <= :now AND r.endDate >= :now")
    Optional<Recruitment> findActiveRecruitment(LocalDateTime now);

    // 기수로 공고 조회
    Optional<Recruitment> findByTerm(Integer term);
}