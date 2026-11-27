package com.boaz.backend.global.security.authz;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.entity.AdminPermissionOverride;
import com.boaz.backend.domain.admin.repository.AdminPermissionOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 유효 권한은 기본 권한에서 REVOKE를 빼고 GRANT를 더한 것이다.
 * <p>권한은 JWT에 담지 않고 요청마다 DB에서 읽는다. 토큰에 담으면 권한을 회수해도 토큰이 만료될 때까지 적용되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class EffectivePermissions {

    private final AdminPermissionOverrideRepository overrideRepository;

    /**
     * 여러 계정의 유효 권한을 한 번에 계산한다. 오버라이드는 계정 수와 상관없이 한 번만 조회한다.
     *
     * @return 계정 id별 유효 권한. 오버라이드가 없는 계정도 기본 권한으로 들어간다
     */
    public Map<Long, Set<Permission>> of(Collection<Admin> admins) {
        Map<Long, Set<Permission>> result = new HashMap<>();
        for (Admin admin : admins) {
            Set<Permission> base = EnumSet.noneOf(Permission.class);
            base.addAll(DefaultPermissions.of(admin.getRole(), admin.getTeamName()));
            result.put(admin.getId(), base);
        }

        List<Long> ids = admins.stream().map(Admin::getId).toList();
        if (ids.isEmpty()) {
            return result;
        }
        for (AdminPermissionOverride o : overrideRepository.findByAdminIdIn(ids)) {
            Set<Permission> permissions = result.get(o.getAdminId());
            if (permissions == null) {
                continue;   // 조회 대상 밖의 행은 무시한다
            }
            apply(permissions, o);
        }
        return result;
    }

    private static void apply(Set<Permission> permissions, AdminPermissionOverride o) {
        if (o.getEffect() == AdminPermissionOverride.Effect.REVOKE) {
            permissions.remove(o.getPermission());
        } else {
            permissions.add(o.getPermission());
        }
    }

    public Set<Permission> of(Admin admin) {
        // 기본 권한이 빈 집합일 수 있어서 EnumSet.copyOf 대신 noneOf로 만든다. copyOf는 빈 컬렉션이면 예외를 던진다.
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        result.addAll(DefaultPermissions.of(admin.getRole(), admin.getTeamName()));

        for (AdminPermissionOverride o : overrideRepository.findByAdminId(admin.getId())) {
            apply(result, o);
        }
        return result;
    }
}
