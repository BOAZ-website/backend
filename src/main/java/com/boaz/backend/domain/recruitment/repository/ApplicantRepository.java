package com.boaz.backend.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.global.common.enums.Track;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

    // 공고에 연관된 지원자 존재 여부 확인
    boolean existsByRecruitmentId(Long recruitmentId);

    // recruitment_id 기반 전체 삭제
    void deleteByRecruitmentId(Long recruitmentId);

    // (recruitment_id, user_id) 조합으로 지원서 조회
    Optional<Applicant> findByRecruitmentIdAndUserId(Long recruitmentId, Long userId);

    // recruitment_id + track + status 기반 조회 (CSV 다운로드용)
    // - JOIN FETCH a.user: user_id 접근 시 N+1 방지
    // - ORDER BY a.submittedAt ASC: 제출 순 정렬 (DRAFT 생성 시점과 분리)
    @Query("SELECT a FROM Applicant a JOIN FETCH a.user " +
           "WHERE a.recruitment.id = :recruitmentId AND a.track = :track AND a.status = :status " +
           "ORDER BY a.submittedAt ASC")
    List<Applicant> findSubmittedByRecruitmentIdAndTrackOrderBySubmittedAt(
            @Param("recruitmentId") Long recruitmentId,
            @Param("track") Track track,
            @Param("status") Applicant.ApplicantStatus status
    );
}
