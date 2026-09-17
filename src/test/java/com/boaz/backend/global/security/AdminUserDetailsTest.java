package com.boaz.backend.global.security;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.security.authz.DefaultPermissions;
import com.boaz.backend.global.security.authz.Permission;
import com.boaz.backend.support.AuthFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2층 전체가 {@code getAuthorities()} 하나에 달려 있다. 여기서 접두사가 붙으면
 * {@code hasAuthority('...')} 표기가 전부 조용히 거부된다.
 */
class AdminUserDetailsTest {

    private List<String> authorityNames(AdminUserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("permission 은 ROLE_ 접두사 없이 authority 로 나간다")
    void permissionsHaveNoRolePrefix() {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀);
        AdminUserDetails details = new AdminUserDetails(admin, Set.of(Permission.CONTENT_WRITE));

        assertThat(authorityNames(details)).contains("CONTENT_WRITE");
        assertThat(authorityNames(details)).doesNotContain("ROLE_CONTENT_WRITE");
    }

    @Test
    @DisplayName("기존 ROLE_<role> authority 는 그대로 유지된다")
    void roleAuthorityKept() {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.MASTER, Admin.TeamName.서비스운영팀);
        AdminUserDetails details = new AdminUserDetails(admin, Set.of());

        assertThat(authorityNames(details)).containsExactly("ROLE_MASTER");
    }

    @Test
    @DisplayName("기본 세트가 전부 authority 로 실린다")
    void allPermissionsExposed() {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.TEAM, Admin.TeamName.운영지원팀);
        Set<Permission> permissions = DefaultPermissions.of(admin.getRole(), admin.getTeamName());
        AdminUserDetails details = new AdminUserDetails(admin, permissions);

        assertThat(authorityNames(details))
                .hasSize(permissions.size() + 1)
                .contains("ROLE_TEAM")
                .containsAll(permissions.stream().map(Enum::name).toList());
    }

    @Test
    @DisplayName("소프트 삭제된 계정은 비활성이다")
    void deletedAdminDisabled() {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀);
        assertThat(new AdminUserDetails(admin, Set.of()).isEnabled()).isTrue();

        admin.softDelete();
        assertThat(new AdminUserDetails(admin, Set.of()).isEnabled()).isFalse();
    }
}
