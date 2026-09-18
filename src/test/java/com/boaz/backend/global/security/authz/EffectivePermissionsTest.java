package com.boaz.backend.global.security.authz;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.entity.AdminPermissionOverride;
import com.boaz.backend.domain.admin.repository.AdminPermissionOverrideRepository;
import com.boaz.backend.support.AuthFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.boaz.backend.global.security.authz.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 기본 세트 − REVOKE + GRANT 병합. 리더십 파생(계산식 3항)은 이번 범위 밖이라 여기서도 다루지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class EffectivePermissionsTest {

    @InjectMocks EffectivePermissions effectivePermissions;

    @Mock AdminPermissionOverrideRepository overrideRepository;

    private AdminPermissionOverride override(Permission permission, AdminPermissionOverride.Effect effect) {
        return AdminPermissionOverride.builder()
                .adminId(1L).permission(permission).effect(effect).grantedBy(99L)
                .build();
    }

    private void givenOverrides(Long adminId, AdminPermissionOverride... overrides) {
        when(overrideRepository.findByAdminId(adminId)).thenReturn(List.of(overrides));
    }

    @Test
    @DisplayName("REVOKE 는 기본 세트에서 실제로 빠진다")
    void revokeRemovesFromBase() {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀);
        givenOverrides(1L, override(CONTENT_WRITE, AdminPermissionOverride.Effect.REVOKE));

        Set<Permission> actual = effectivePermissions.of(admin);

        assertThat(DefaultPermissions.of(Admin.Role.TEAM, Admin.TeamName.서비스운영팀))
                .contains(CONTENT_WRITE);  // 애초에 갖고 있던 권한이어야 REVOKE 검증이 성립한다
        assertThat(actual).doesNotContain(CONTENT_WRITE);
        assertThat(actual).contains(CONTENT_READ);
    }

    @Test
    @DisplayName("GRANT 는 기본 세트에 없던 권한을 더한다")
    void grantAddsToBase() {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀);
        givenOverrides(1L, override(APPLICANT_CSV_READ, AdminPermissionOverride.Effect.GRANT));

        assertThat(effectivePermissions.of(admin)).containsExactlyInAnyOrder(
                ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ,
                APPLICANT_CSV_READ);
    }

    @Test
    @DisplayName("GRANT 와 REVOKE 가 섞여도 각각 적용된다")
    void grantAndRevokeTogether() {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.TEAM, Admin.TeamName.서비스운영팀);
        givenOverrides(1L,
                override(AUDIT_LOG_READ, AdminPermissionOverride.Effect.REVOKE),
                override(ATTENDANCE_BASE_READ, AdminPermissionOverride.Effect.GRANT));

        Set<Permission> actual = effectivePermissions.of(admin);

        assertThat(actual).doesNotContain(AUDIT_LOG_READ);
        assertThat(actual).contains(ATTENDANCE_BASE_READ);
    }

    @Test
    @DisplayName("기본 세트에 없는 권한을 REVOKE 해도 아무 일도 없다")
    void revokeOfUnheldPermission() {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.TEAM, Admin.TeamName.기획팀);
        givenOverrides(1L, override(ATTENDANCE_BASE_WRITE, AdminPermissionOverride.Effect.REVOKE));

        assertThat(effectivePermissions.of(admin))
                .isEqualTo(DefaultPermissions.of(Admin.Role.TEAM, Admin.TeamName.기획팀));
    }

    @Test
    @DisplayName("권한 0인 조합에도 GRANT 를 얹을 수 있다 — 빈 기본 세트에서 터지지 않는다")
    void grantOnEmptyBase() {
        Admin admin = AuthFixtures.admin(1L, Admin.Role.SUPER, Admin.TeamName.그룹리더);
        givenOverrides(1L, override(ADMIN_ACCOUNT_SELF_WRITE, AdminPermissionOverride.Effect.GRANT));

        assertThat(DefaultPermissions.of(Admin.Role.SUPER, Admin.TeamName.그룹리더)).isEmpty();
        assertThat(effectivePermissions.of(admin)).containsExactly(ADMIN_ACCOUNT_SELF_WRITE);
    }
}
