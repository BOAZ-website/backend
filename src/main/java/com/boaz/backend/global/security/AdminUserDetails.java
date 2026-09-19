package com.boaz.backend.global.security;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.security.authz.Permission;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

public class AdminUserDetails implements UserDetails {

    private final Admin admin;
    private final Set<Permission> permissions;

    /**
     * @param permissions 유효 권한. {@code EffectivePermissions}가 매 요청 계산해 넘긴다.
     *                    빈 집합이면 authority는 {@code ROLE_<role>} 하나뿐이라 컨트롤러에서 전부 막힌다.
     */
    public AdminUserDetails(Admin admin, Set<Permission> permissions) {
        this.admin = admin;
        this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public Admin getAdmin() {
        return admin;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    /**
     * permission은 {@code ROLE_} 접두사 <b>없이</b> 나간다 —
     * 그래서 {@code @PreAuthorize}는 {@code hasRole}이 아니라 {@code hasAuthority}로 적어야 한다.
     * 기존 {@code ROLE_<role>}은 URL 게이트의 USER/ADMIN 구분과 남아 있는 role 비교가 쓰므로 그대로 둔다.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Stream.concat(
                Stream.of(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name())),
                permissions.stream().map(p -> new SimpleGrantedAuthority(p.name()))
        ).toList();
    }

    @Override
    public String getPassword() {
        return admin.getPassword();
    }

    @Override
    public String getUsername() {
        return admin.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return !admin.isDeleted();
    }
}
