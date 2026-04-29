package com.boaz.backend.domain.recruitment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.global.common.enums.Track;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

    // 공고에 연관된 지원자 존재 여부 확인
    boolean existsByRecruitmentId(Long recruitmentId);

    // recruitment_id 기반 지원자 ID 목록 조회
    @Query("SELECT a.id FROM Applicant a WHERE a.recruitment.id = :recruitmentId")
    List<Long> findIdsByRecruitmentId(@Param("recruitmentId") Long recruitmentId);

    // recruitment_id 기반 전체 삭제
    void deleteByRecruitmentId(Long recruitmentId);

    // recruitment_id, track 기반 검색 (중복 제거)
    @Query("""
        SELECT a FROM Applicant a
        WHERE a.recruitment.id = :recruitmentId
        AND a.track = :track
        AND a.createdAt = (
            SELECT MAX(a2.createdAt) FROM Applicant a2
            WHERE a2.email = a.email
            AND a2.recruitment.id = :recruitmentId
        )
        ORDER BY a.createdAt ASC
    """)
    List<Applicant> findLatestByRecruitmentIdAndTrack(
            @Param("recruitmentId") Long recruitmentId,
            @Param("track") Track track
    );
}