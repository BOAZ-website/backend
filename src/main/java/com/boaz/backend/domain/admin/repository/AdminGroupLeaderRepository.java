package com.boaz.backend.domain.admin.repository;

import com.boaz.backend.domain.admin.entity.AdminGroupLeader;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 리더십 파생과 {@code ScopeGuard} 축 ②가 쓸 리포지토리.
 */
public interface AdminGroupLeaderRepository extends JpaRepository<AdminGroupLeader, Long> {

    List<AdminGroupLeader> findByAdminId(Long adminId);
}
