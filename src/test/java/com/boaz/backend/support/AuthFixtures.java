package com.boaz.backend.support;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.security.AdminUserDetails;
import com.boaz.backend.global.security.UserPrincipal;
import com.boaz.backend.global.security.authz.DefaultPermissions;
import com.boaz.backend.global.security.authz.Permission;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

/**
 * 인증 주체를 만드는 공용 픽스처. 테스트마다 {@code new AdminUserDetails(...)}와 authority 문자열을
 * 찍던 것을 여기로 모은다.
 * <p>기본값은 <b>기본 세트 그대로</b>다({@link DefaultPermissions}). 즉 여기서 만든 주체는 실제 운영에서
 * 그 자리가 갖는 권한과 같은 authority를 들고 다니므로, 2층 판정을 그대로 검증할 수 있다.
 * 오버라이드가 섞인 상황을 보려면 permission 집합을 직접 넘기는 오버로드를 쓴다.
 */
public final class AuthFixtures {

    public static final Track DEFAULT_TRACK = Track.ANALYSIS;
    public static final int DEFAULT_TERM = 25;

    private AuthFixtures() {
    }

    public static Admin admin(Long id, Admin.Role role, Admin.TeamName teamName) {
        return admin(id, role, teamName, DEFAULT_TRACK);
    }

    public static Admin admin(Long id, Admin.Role role, Admin.TeamName teamName, Track track) {
        Admin admin = Admin.builder()
                .username("user" + id).password("ENC").role(role).name("name" + id)
                .track(track).term(DEFAULT_TERM).teamName(teamName).createdBy(null)
                .build();
        ReflectionTestUtils.setField(admin, "id", id);
        return admin;
    }

    /** 기본 세트 권한을 가진 주체. */
    public static AdminUserDetails details(Admin admin) {
        return new AdminUserDetails(admin, DefaultPermissions.of(admin.getRole(), admin.getTeamName()));
    }

    /** 권한을 직접 지정한 주체 — 오버라이드가 섞인 상황이나 권한 0인 상황을 만들 때 쓴다. */
    public static AdminUserDetails details(Admin admin, Set<Permission> permissions) {
        return new AdminUserDetails(admin, permissions);
    }

    public static UsernamePasswordAuthenticationToken adminAuth(Admin admin) {
        return authenticationOf(details(admin));
    }

    public static UsernamePasswordAuthenticationToken adminAuth(Admin admin, Set<Permission> permissions) {
        return authenticationOf(details(admin, permissions));
    }

    public static UsernamePasswordAuthenticationToken adminAuth(Long id, Admin.Role role, Admin.TeamName teamName) {
        return adminAuth(admin(id, role, teamName));
    }

    public static UsernamePasswordAuthenticationToken adminAuth(Long id, Admin.Role role,
                                                                Admin.TeamName teamName, Track track) {
        return adminAuth(admin(id, role, teamName, track));
    }

    public static UsernamePasswordAuthenticationToken userAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static UsernamePasswordAuthenticationToken authenticationOf(AdminUserDetails principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
