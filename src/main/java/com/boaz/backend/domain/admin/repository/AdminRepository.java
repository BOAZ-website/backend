package com.boaz.backend.domain.admin.repository;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUsernameAndDeletedAtIsNull(String username);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    List<Admin> findAllByDeletedAtIsNullOrderByCreatedAtAsc();

    Optional<Admin> findByIdAndDeletedAtIsNull(Long id);

    long countByRoleAndDeletedAtIsNull(Admin.Role role);

    // 지원서별 평가 조회 — 해당 부문 평가자 풀 (미평가자 null 포함용)
    List<Admin> findByTrackAndDeletedAtIsNullOrderByNameAsc(Track track);

    // 평가자 풀 = 해당 부문 + 전 부문 평가 권한자(차기 대표진). 차기 대표진은 본인 track과 무관하게 모든 지원자 풀에 포함.
    @Query("SELECT DISTINCT a FROM Admin a WHERE a.deletedAt IS NULL " +
           "AND (a.track = :track OR (a.role = :allTrackRole AND a.teamName = :allTrackTeam)) " +
           "ORDER BY a.name ASC")
    List<Admin> findEvaluatorPool(@Param("track") Track track,
                                  @Param("allTrackRole") Admin.Role allTrackRole,
                                  @Param("allTrackTeam") Admin.TeamName allTrackTeam);
}
