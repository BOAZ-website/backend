package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecruitmentRepository extends JpaRepository<Recruitment, Long> {

    // 현재 모집 중인 공고 조회 (현재 시간이 start_date ~ end_date 사이)
    @Query("SELECT r FROM Recruitment r WHERE r.startDate <= :now AND r.endDate >= :now")
    Optional<Recruitment> findActiveRecruitment(LocalDateTime now);

    // 진행 중이거나 아직 마감되지 않은 공고 중 가장 임박한 것 (예정 포함)
    @Query("SELECT r FROM Recruitment r WHERE r.endDate >= :now ORDER BY r.startDate ASC LIMIT 1")
    Optional<Recruitment> findCurrentOrUpcoming(LocalDateTime now);

    // 기수로 공고 조회
    Optional<Recruitment> findByTerm(Integer term);

    // 전체 공고 term 내림차순 조회
    List<Recruitment> findAllByOrderByTermDesc();

    // 기수 중복 확인
    boolean existsByTerm(Integer term);

    // 수정 시 기수 중복 확인 (자기 자신 제외)
    boolean existsByTermAndIdNot(Integer term, Long id);
}