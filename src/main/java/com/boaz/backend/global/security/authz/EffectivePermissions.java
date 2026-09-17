package com.boaz.backend.global.security.authz;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.entity.AdminPermissionOverride;
import com.boaz.backend.domain.admin.repository.AdminPermissionOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * 유효 권한 계산기.
 *
 * <pre>
 * 유효 권한 = DefaultPermissions(role, teamName)   ← 코드.  운영진으로서의 자리
 *           − REVOKE + GRANT                       ← DB.   계정별 오버라이드
 *           + LeadershipPermissions(admin)         ← DB.   그룹 리더십 (아직 구현 전)
 * </pre>
 *
 * <p><b>세 번째 항은 아직 없다.</b> 리더십 파생은 {@code admin_group_leader ⋈ activity_group}의 그룹 타입을
 * 봐야 하는데 {@code activity_group} 테이블이 아직 없다. 그래서 지금은 {@code _OWN_GROUP_} permission을
 * 아무도 갖지 못한다 — 그 permission을 참조하는 {@code @PreAuthorize}도 0개라 소비처가 없는 상태다.
 *
 * <p><b>권한은 JWT claim에 싣지 않는다.</b> 매 요청 DB로 읽는다. 토큰에 담으면 권한 회수가 토큰 만료 전까지
 * 안 먹힌다.
 */
@Component
@RequiredArgsConstructor
public class EffectivePermissions {

    private final AdminPermissionOverrideRepository overrideRepository;

    public Set<Permission> of(Admin admin) {
        // EnumSet.copyOf 는 빈 컬렉션에서 던진다 — 권한 0인 조합(폴백에 없는 role)이 실제로 존재하므로
        // noneOf + addAll 로 만든다.
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        result.addAll(DefaultPermissions.of(admin.getRole(), admin.getTeamName()));

        for (AdminPermissionOverride o : overrideRepository.findByAdminId(admin.getId())) {
            if (o.getEffect() == AdminPermissionOverride.Effect.REVOKE) {
                result.remove(o.getPermission());
            } else {
                result.add(o.getPermission());
            }
        }
        return result;
    }
}
