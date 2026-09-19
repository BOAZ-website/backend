package com.boaz.backend.domain.admin.repository;

import com.boaz.backend.domain.admin.entity.AdminPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AdminPermissionOverrideRepository extends JpaRepository<AdminPermissionOverride, Long> {

    /** 매 요청 유효 권한 계산에서 호출된다. */
    List<AdminPermissionOverride> findByAdminId(Long adminId);

    /**
     * 여러 계정의 오버라이드를 한 번에 읽는다. 락아웃 검사에서 N+1쿼리를 방지하기 위함.
     */
    List<AdminPermissionOverride> findByAdminIdIn(Collection<Long> adminIds);

    /**
     * 권한 커스텀 저장(전삭제 + 재삽입)과 role·teamName 변경 시 오버라이드 정리에서 쓴다.
     */
    void deleteByAdminId(Long adminId);
}
