package com.boaz.backend.domain.admin.repository;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
