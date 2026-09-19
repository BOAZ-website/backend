package com.boaz.backend.domain.admin.service;

import com.boaz.backend.domain.admin.dto.request.AdminCreateRequest;
import com.boaz.backend.domain.admin.dto.request.AdminPasswordResetRequest;
import com.boaz.backend.domain.admin.dto.request.AdminUpdateRequest;
import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.dto.response.AdminIdResponse;
import com.boaz.backend.domain.admin.dto.response.AdminMeResponse;
import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminPermissionOverrideRepository;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.global.common.enums.AccountType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.authz.EffectivePermissions;
import com.boaz.backend.global.security.authz.Permission;
import com.boaz.backend.global.security.authz.ScopeGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 4단계 이후의 {@code AdminService} — <b>인가 판정이 여기 없다.</b>
 *
 * <p>permission 보유 여부는 2층({@code AdminPermissionGridTest})이, 대상이 본인이냐는 3층
 * ({@link ScopeGuard})이 본다. 그래서 옛 "TEAM 호출 → ACCESS_DENIED" 케이스들은 이 파일에서
 * 사라졌고, 대신 <b>{@code ScopeGuard}가 거부하면 서비스가 더 나아가지 않는다</b>는 것만 확인한다.
 *
 * <p>주체 기본값은 {@code (MASTER, 서비스운영팀)}이다 — 계정 CRUD 권한을 다 가진 유일한 키다.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @InjectMocks AdminService adminService;

    @Mock AdminRepository adminRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock AdminPermissionOverrideRepository overrideRepository;
    @Mock EffectivePermissions effectivePermissions;
    @Mock ScopeGuard scopeGuard;

    /**
     * 컨트롤러가 {@code AdminUserDetails} 에서 꺼내 넘기는 유효 권한 자리. 여기서는 {@code ScopeGuard} 가
     * 목이라 값 자체는 판정에 쓰이지 않고, 스텁·검증 인자와 맞추기 위해 하나로 고정한다.
     * 실제 권한에 따라 갈리는 판정은 {@code ScopeGuardTest} 가 본다.
     */
    private static final Set<Permission> PERMISSIONS = Set.of();

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    /** 계정 CRUD 권한을 다 가진 주체. 옛 테스트의 SUPER 자리를 대신한다. */
    private Admin master(Long id) {
        return adminWith(id, Admin.Role.MASTER, Track.ANALYSIS, "name" + id, Admin.TeamName.서비스운영팀);
    }

    private Admin admin(Long id, Admin.Role role) {
        return adminWith(id, role, Track.ANALYSIS, "name" + id, Admin.TeamName.기획팀);
    }

    private Admin adminWith(Long id, Admin.Role role, Track track, String name, Admin.TeamName team) {
        Admin a = Admin.builder()
                .username("user" + id).password("OLD_HASH").role(role).name(name)
                .track(track).term(25).teamName(team).createdBy(null)
                .build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private AdminCreateRequest createRequest(String username, Track track) {
        AdminCreateRequest req = new AdminCreateRequest();
        ReflectionTestUtils.setField(req, "username", username);
        ReflectionTestUtils.setField(req, "password", "Boaz1234!");
        ReflectionTestUtils.setField(req, "role", Admin.Role.TEAM);
        ReflectionTestUtils.setField(req, "name", "김보아즈");
        ReflectionTestUtils.setField(req, "track", track);
        ReflectionTestUtils.setField(req, "term", 25);
        ReflectionTestUtils.setField(req, "teamName", Admin.TeamName.기획팀);
        return req;
    }

    private AdminPasswordResetRequest pwRequest(String current, String newPw) {
        AdminPasswordResetRequest req = new AdminPasswordResetRequest();
        ReflectionTestUtils.setField(req, "currentPassword", current);
        ReflectionTestUtils.setField(req, "newPassword", newPw);
        return req;
    }

    // ──────────────────────────────────────────────
    // ADMIN-001: getAccounts (모든 계정 조회)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-001 getAccounts")
    class GetAccounts {

        @Test
        @DisplayName("TC-001 조회 → created_at 오름차순 목록 매핑")
        void list() {
            when(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(master(1L), admin(2L, Admin.Role.TEAM)));

            List<AdminAccountResponse> result = adminService.getAccounts();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AdminAccountResponse::getId).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("TC-002 계정 없음 → 빈 배열 (예외 없음)")
        void empty() {
            when(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());

            assertThat(adminService.getAccounts()).isEmpty();
        }

    }

    // ──────────────────────────────────────────────
    // ADMIN-002: createAccount (계정 생성)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-002 createAccount")
    class CreateAccount {

        @Test
        @DisplayName("TC-001 생성 → 201, password 인코딩 + createdBy 기록")
        void success() {
            Admin currentAdmin = master(1L);
            AdminCreateRequest req = createRequest("boaz_team2", Track.ANALYSIS);
            when(adminRepository.existsByUsernameAndDeletedAtIsNull("boaz_team2")).thenReturn(false);
            when(passwordEncoder.encode("Boaz1234!")).thenReturn("ENCODED");
            when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> {
                Admin a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 13L);
                return a;
            });

            AdminIdResponse res = adminService.createAccount(req, currentAdmin);

            assertThat(res.getId()).isEqualTo(13L);
            ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
            verify(adminRepository).save(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("ENCODED");
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(1L);
        }

        @Test
        @DisplayName("TC-010 매트릭스에 없는 (role, 소속) 조합 → INVALID_ROLE_TEAM_COMBINATION, save 안 함")
        void invalidRoleTeamCombination() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            AdminCreateRequest req = createRequest("boaz_bad", Track.ANALYSIS);
            // (TEAM, 대표진) — 폴백이 role 만 보던 시절에는 평가·최종결정 권한까지 받았다
            ReflectionTestUtils.setField(req, "teamName", Admin.TeamName.대표진);

            assertThatThrownBy(() -> adminService.createAccount(req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ROLE_TEAM_COMBINATION);
            verify(adminRepository, never()).save(any(Admin.class));
        }

        @Test
        @DisplayName("TC-011 유효하지 않은 조합은 username 중복 검사보다 먼저 걸린다")
        void combinationCheckedBeforeDuplicateUsername() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            AdminCreateRequest req = createRequest("boaz_bad", Track.ANALYSIS);
            ReflectionTestUtils.setField(req, "role", Admin.Role.HOST);   // (HOST, 기획팀)

            assertThatThrownBy(() -> adminService.createAccount(req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ROLE_TEAM_COMBINATION);
            verify(adminRepository, never()).existsByUsernameAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("TC-003 track=ALL → INVALID_TRACK_SELECTION, save 안 함")
        void trackAll() {
            AdminCreateRequest req = createRequest("boaz_team2", Track.ALL);

            assertThatThrownBy(() -> adminService.createAccount(req, master(1L)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
            verify(adminRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-004 username 중복 → DUPLICATE_USERNAME, save 안 함")
        void duplicateUsername() {
            AdminCreateRequest req = createRequest("boaz_team2", Track.ANALYSIS);
            when(adminRepository.existsByUsernameAndDeletedAtIsNull("boaz_team2")).thenReturn(true);

            assertThatThrownBy(() -> adminService.createAccount(req, master(1L)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_USERNAME);
            verify(adminRepository, never()).save(any());
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-007: getMe (내 계정 정보 조회)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-007 getMe")
    class GetMe {

        @Test
        @DisplayName("TC-001 본인 정보 → id/team_name/name 매핑, repository 미상호작용")
        void self() {
            Admin currentAdmin = adminWith(1L, Admin.Role.SUPER, Track.ANALYSIS, "문혁준", Admin.TeamName.대표진);

            AdminMeResponse res = adminService.getMe(currentAdmin);

            assertThat(res.getId()).isEqualTo(1L);
            assertThat(res.getName()).isEqualTo("문혁준");
            assertThat(res.getTeamName()).isEqualTo("대표진");
            verifyNoInteractions(adminRepository, refreshTokenRepository, passwordEncoder);
        }

        @Test
        @DisplayName("TC-002 권한 0인 주체도 호출 가능 (권한 거부 없음)")
        void anySubjectAllowed() {
            Admin currentAdmin = adminWith(5L, Admin.Role.HOST, Track.ENGINEERING, "홍길동", Admin.TeamName.그룹리더);

            AdminMeResponse res = adminService.getMe(currentAdmin);

            assertThat(res.getId()).isEqualTo(5L);
            assertThat(res.getTeamName()).isEqualTo("그룹리더");
            verifyNoInteractions(scopeGuard);
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-003: getAccount (id별 계정 조회)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-003 getAccount")
    class GetAccount {

        @Test
        @DisplayName("TC-001 타 계정 조회 → 반환 (ScopeGuard 통과)")
        void other() {
            Admin currentAdmin = master(1L);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L))
                    .thenReturn(Optional.of(admin(2L, Admin.Role.TEAM)));

            AdminAccountResponse res = adminService.getAccount(2L, currentAdmin, PERMISSIONS);

            assertThat(res.getId()).isEqualTo(2L);
            verify(scopeGuard).checkAccountRead(currentAdmin, PERMISSIONS, 2L);
        }

        @Test
        @DisplayName("TC-002 본인 계정 조회 → 반환")
        void self() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(currentAdmin));

            assertThat(adminService.getAccount(5L, currentAdmin, PERMISSIONS).getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("TC-003 ScopeGuard 거부 → ACCESS_DENIED (DB 조회 안 함)")
        void scopeDenied() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            doThrow(new CustomException(ErrorCode.ACCESS_DENIED))
                    .when(scopeGuard).checkAccountRead(currentAdmin, PERMISSIONS, 2L);

            assertThatThrownBy(() -> adminService.getAccount(2L, currentAdmin, PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-004 존재하지 않는 계정 → ADMIN_NOT_FOUND")
        void notFound() {
            when(adminRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getAccount(999L, master(1L), PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_NOT_FOUND);
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-004: updateAccount (id별 계정 수정)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-004 updateAccount")
    class UpdateAccount {

        @Test
        @DisplayName("TC-001 프로필 필드만 수정 → 변경, 토큰·오버라이드 그대로")
        void profileOnly() {
            Admin target = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "name", JsonNullable.of("새이름"));

            AdminIdResponse res = adminService.updateAccount(2L, req, master(1L), PERMISSIONS);

            assertThat(res.getId()).isEqualTo(2L);
            assertThat(target.getName()).isEqualTo("새이름");
            verify(refreshTokenRepository, never()).deleteByAccountTypeAndAccountId(any(), anyLong());
            verify(overrideRepository, never()).deleteByAdminId(anyLong());
            verifyNoInteractions(effectivePermissions);
        }

        @Test
        @DisplayName("TC-002 타 계정 role 변경 → 변경 + RefreshToken 삭제 + 오버라이드 전삭제")
        void roleChange() {
            Admin target = admin(2L, Admin.Role.TEAM);   // (TEAM, 기획팀)
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            when(effectivePermissions.of(target)).thenReturn(Set.of());
            AdminUpdateRequest req = new AdminUpdateRequest();
            // role 만 SUPER 로 올리면 (SUPER, 기획팀) 이라 매트릭스에 없는 조합이 된다.
            // 실제 승격은 소속도 같이 바뀌므로 둘을 함께 보낸다.
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.SUPER));
            ReflectionTestUtils.setField(req, "teamName", JsonNullable.of(Admin.TeamName.대표진));

            adminService.updateAccount(2L, req, master(1L), PERMISSIONS);

            assertThat(target.getRole()).isEqualTo(Admin.Role.SUPER);
            assertThat(target.getTeamName()).isEqualTo(Admin.TeamName.대표진);
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 2L);
            verify(overrideRepository).deleteByAdminId(2L);
        }

        @Test
        @DisplayName("TC-010 role 만 올려 매트릭스에 없는 조합이 되면 → INVALID_ROLE_TEAM_COMBINATION")
        void roleOnlyChangeBreakingCombination() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            Admin target = admin(2L, Admin.Role.TEAM);   // (TEAM, 기획팀)
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.SUPER));

            assertThatThrownBy(() -> adminService.updateAccount(2L, req, currentAdmin, PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ROLE_TEAM_COMBINATION);
            assertThat(target.getRole()).isEqualTo(Admin.Role.TEAM);   // 변경되지 않았다
        }

        @Test
        @DisplayName("TC-011 teamName 만 바꿔 매트릭스에 없는 조합이 되면 → INVALID_ROLE_TEAM_COMBINATION")
        void teamOnlyChangeBreakingCombination() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            Admin target = admin(2L, Admin.Role.TEAM);   // (TEAM, 기획팀)
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "teamName", JsonNullable.of(Admin.TeamName.대표진));

            assertThatThrownBy(() -> adminService.updateAccount(2L, req, currentAdmin, PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ROLE_TEAM_COMBINATION);
            assertThat(target.getTeamName()).isEqualTo(Admin.TeamName.기획팀);
        }

        @Test
        @DisplayName("TC-012 권한 키를 안 건드리면 조합을 보지 않는다 — 기존 잘못된 행도 이름은 고칠 수 있다")
        void nonKeyChangeSkipsCombinationCheck() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            // 이 검증이 생기기 전에 저장됐을 법한 조합
            Admin target = adminWith(2L, Admin.Role.SUPER, Track.ANALYSIS, "옛이름", Admin.TeamName.기획팀);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "name", JsonNullable.of("새이름"));

            adminService.updateAccount(2L, req, currentAdmin, PERMISSIONS);

            assertThat(target.getName()).isEqualTo("새이름");
        }

        @Test
        @DisplayName("TC-003 teamName 변경도 권한 키 변경이다 → 토큰·오버라이드 정리")
        void teamNameChange() {
            Admin target = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            when(effectivePermissions.of(target)).thenReturn(Set.of());
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "teamName", JsonNullable.of(Admin.TeamName.운영지원팀));

            adminService.updateAccount(2L, req, master(1L), PERMISSIONS);

            assertThat(target.getTeamName()).isEqualTo(Admin.TeamName.운영지원팀);
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 2L);
            verify(overrideRepository).deleteByAdminId(2L);
        }

        @Test
        @DisplayName("TC-004 ScopeGuard 거부 → ACCESS_DENIED (DB 조회 안 함)")
        void scopeDenied() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            AdminUpdateRequest req = new AdminUpdateRequest();
            doThrow(new CustomException(ErrorCode.ACCESS_DENIED))
                    .when(scopeGuard).checkAccountWrite(currentAdmin, PERMISSIONS, 2L);

            assertThatThrownBy(() -> adminService.updateAccount(2L, req, currentAdmin, PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-005 본인 role 변경 → CANNOT_MODIFY_OWN_ROLE (DB 조회 안 함)")
        void selfRole() {
            Admin currentAdmin = master(1L);
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.TEAM));

            assertThatThrownBy(() -> adminService.updateAccount(1L, req, currentAdmin, PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ROLE);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-006 본인 teamName 변경도 금지 → CANNOT_MODIFY_OWN_ROLE (권한 키의 절반이다)")
        void selfTeamName() {
            Admin currentAdmin = master(1L);
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "teamName", JsonNullable.of(Admin.TeamName.기획팀));

            assertThatThrownBy(() -> adminService.updateAccount(1L, req, currentAdmin, PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ROLE);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-007 본인 프로필(이름)만 수정 → 허용. 막는 것은 권한 키뿐이다")
        void selfProfileAllowed() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(currentAdmin));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "name", JsonNullable.of("새이름"));

            adminService.updateAccount(5L, req, currentAdmin, PERMISSIONS);

            assertThat(currentAdmin.getName()).isEqualTo("새이름");
        }

        @Test
        @DisplayName("TC-008 존재하지 않는 계정 → ADMIN_NOT_FOUND")
        void notFound() {
            when(adminRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());
            AdminUpdateRequest req = new AdminUpdateRequest();

            assertThatThrownBy(() -> adminService.updateAccount(999L, req, master(1L), PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-009 track=ALL → INVALID_TRACK_SELECTION")
        void trackAll() {
            when(adminRepository.findByIdAndDeletedAtIsNull(2L))
                    .thenReturn(Optional.of(admin(2L, Admin.Role.TEAM)));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "track", JsonNullable.of(Track.ALL));

            assertThatThrownBy(() -> adminService.updateAccount(2L, req, master(1L), PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
        }

        @Test
        @DisplayName("TC-010 term < 0 → INVALID_INPUT_VALUE")
        void negativeTerm() {
            when(adminRepository.findByIdAndDeletedAtIsNull(2L))
                    .thenReturn(Optional.of(admin(2L, Admin.Role.TEAM)));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "term", JsonNullable.of(-1));

            assertThatThrownBy(() -> adminService.updateAccount(2L, req, master(1L), PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("TC-011 마지막 ADMIN_ACCOUNT_CREATE_DELETE 보유자 강등 → LAST_ACCOUNT_MANAGER (변경 안 됨)")
        void lastManagerDemotionBlocked() {
            Admin target = master(2L);
            Admin other = admin(3L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            when(effectivePermissions.of(target)).thenReturn(Set.of(Permission.ADMIN_ACCOUNT_CREATE_DELETE));
            when(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(target, other));
            when(effectivePermissions.of(List.of(other))).thenReturn(Map.of(other.getId(), Set.of()));

            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.TEAM));

            assertThatThrownBy(() -> adminService.updateAccount(2L, req, master(1L), PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.LAST_ACCOUNT_MANAGER);
            assertThat(target.getRole()).isEqualTo(Admin.Role.MASTER);
            verify(overrideRepository, never()).deleteByAdminId(anyLong());
        }

        @Test
        @DisplayName("TC-012 다른 보유자가 남아 있으면 강등 허용 — 기준은 role 이 아니라 유효 권한 카운트다")
        void demotionAllowedWhenAnotherManagerRemains() {
            Admin target = master(2L);
            Admin other = master(3L);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            when(effectivePermissions.of(target)).thenReturn(Set.of(Permission.ADMIN_ACCOUNT_CREATE_DELETE));
            when(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(target, other));
            when(effectivePermissions.of(List.of(other)))
                    .thenReturn(Map.of(other.getId(), Set.of(Permission.ADMIN_ACCOUNT_CREATE_DELETE)));

            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.TEAM));

            adminService.updateAccount(2L, req, master(1L), PERMISSIONS);

            assertThat(target.getRole()).isEqualTo(Admin.Role.TEAM);
            verify(overrideRepository).deleteByAdminId(2L);
        }

        @Test
        @DisplayName("TC-013 권한을 잃지 않는 키 변경은 카운트를 안 센다")
        void noCountWhenPermissionKept() {
            Admin target = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            when(effectivePermissions.of(target)).thenReturn(Set.of());

            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.MASTER));
            ReflectionTestUtils.setField(req, "teamName", JsonNullable.of(Admin.TeamName.서비스운영팀));

            adminService.updateAccount(2L, req, master(1L), PERMISSIONS);

            verify(adminRepository, never()).findAllByDeletedAtIsNullOrderByCreatedAtAsc();
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-005: deleteAccount (id별 계정 삭제, soft delete)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-005 deleteAccount")
    class DeleteAccount {

        @Test
        @DisplayName("TC-001 계정 관리 권한이 없는 대상 삭제 → soft delete + RefreshToken 삭제 (카운트 안 셈)")
        void deletesOther() {
            Admin target = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            when(effectivePermissions.of(target)).thenReturn(Set.of());

            adminService.deleteAccount(2L);

            assertThat(target.isDeleted()).isTrue();
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 2L);
            verify(adminRepository, never()).findAllByDeletedAtIsNullOrderByCreatedAtAsc();
        }

        @Test
        @DisplayName("TC-002 다른 보유자 존재 → 계정 관리자 삭제 허용")
        void deletesManagerWhenAnotherRemains() {
            Admin target = master(1L);
            Admin other = master(2L);
            when(adminRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(target));
            when(effectivePermissions.of(target)).thenReturn(Set.of(Permission.ADMIN_ACCOUNT_CREATE_DELETE));
            when(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(target, other));
            when(effectivePermissions.of(List.of(other)))
                    .thenReturn(Map.of(other.getId(), Set.of(Permission.ADMIN_ACCOUNT_CREATE_DELETE)));

            adminService.deleteAccount(1L);

            assertThat(target.isDeleted()).isTrue();
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 1L);
        }

        @Test
        @DisplayName("TC-003 마지막 계정 관리자 삭제 시도 → LAST_ACCOUNT_MANAGER")
        void lastManagerBlocked() {
            Admin target = master(1L);
            Admin other = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(target));
            when(effectivePermissions.of(target)).thenReturn(Set.of(Permission.ADMIN_ACCOUNT_CREATE_DELETE));
            when(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(target, other));
            when(effectivePermissions.of(List.of(other))).thenReturn(Map.of(other.getId(), Set.of()));

            assertThatThrownBy(() -> adminService.deleteAccount(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.LAST_ACCOUNT_MANAGER);
            assertThat(target.isDeleted()).isFalse();
            verify(refreshTokenRepository, never()).deleteByAccountTypeAndAccountId(any(), anyLong());
        }

        @Test
        @DisplayName("TC-004 오버라이드로 권한을 받은 계정도 보유자로 센다 — role 로 세지 않는다")
        void grantedByOverrideCounts() {
            Admin target = master(1L);
            Admin granted = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(target));
            when(effectivePermissions.of(target)).thenReturn(Set.of(Permission.ADMIN_ACCOUNT_CREATE_DELETE));
            when(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(target, granted));
            // role 은 TEAM 이지만 GRANT 오버라이드로 유효 권한을 갖는다.
            when(effectivePermissions.of(List.of(granted)))
                    .thenReturn(Map.of(granted.getId(), Set.of(Permission.ADMIN_ACCOUNT_CREATE_DELETE)));

            adminService.deleteAccount(1L);

            assertThat(target.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("TC-005 존재하지 않는 계정 → ADMIN_NOT_FOUND")
        void notFound() {
            when(adminRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.deleteAccount(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_NOT_FOUND);
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-006: resetPassword (비밀번호 초기화/변경)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-006 resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("TC-001 타인 초기화 (currentPassword 불필요) → 변경 + 토큰 삭제, matches 미호출")
        void resetsOther() {
            Admin currentAdmin = master(1L);
            Admin target = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            when(passwordEncoder.encode("NewBoaz1234!")).thenReturn("ENC2");

            adminService.resetPassword(2L, pwRequest(null, "NewBoaz1234!"), currentAdmin, PERMISSIONS);

            assertThat(target.getPassword()).isEqualTo("ENC2");
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 2L);
            verify(passwordEncoder, never()).matches(any(), any());
            verify(scopeGuard).checkAccountWrite(currentAdmin, PERMISSIONS, 2L);
        }

        @Test
        @DisplayName("TC-002 본인 변경 (currentPassword 일치) → 변경 + 토큰 삭제")
        void selfChangeMatches() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(currentAdmin));
            when(passwordEncoder.matches("old", "OLD_HASH")).thenReturn(true);
            when(passwordEncoder.encode("NewBoaz1234!")).thenReturn("ENC2");

            adminService.resetPassword(5L, pwRequest("old", "NewBoaz1234!"), currentAdmin, PERMISSIONS);

            assertThat(currentAdmin.getPassword()).isEqualTo("ENC2");
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 5L);
        }

        @Test
        @DisplayName("TC-003 본인 변경인데 currentPassword 누락 → INVALID_INPUT_VALUE")
        void selfCurrentPasswordBlank() {
            Admin currentAdmin = master(1L);
            when(adminRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(currentAdmin));

            assertThatThrownBy(() -> adminService.resetPassword(1L, pwRequest(null, "NewBoaz1234!"), currentAdmin, PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            verify(refreshTokenRepository, never()).deleteByAccountTypeAndAccountId(any(), anyLong());
        }

        @Test
        @DisplayName("TC-004 본인 변경인데 currentPassword 불일치 → INVALID_CURRENT_PASSWORD")
        void selfCurrentPasswordMismatch() {
            Admin currentAdmin = master(1L);
            when(adminRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(currentAdmin));
            when(passwordEncoder.matches("wrong", "OLD_HASH")).thenReturn(false);

            assertThatThrownBy(() -> adminService.resetPassword(1L, pwRequest("wrong", "NewBoaz1234!"), currentAdmin, PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);
            verify(refreshTokenRepository, never()).deleteByAccountTypeAndAccountId(any(), anyLong());
        }

        @Test
        @DisplayName("TC-005 ScopeGuard 거부 → ACCESS_DENIED (DB 조회 안 함)")
        void scopeDenied() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            doThrow(new CustomException(ErrorCode.ACCESS_DENIED))
                    .when(scopeGuard).checkAccountWrite(currentAdmin, PERMISSIONS, 2L);

            assertThatThrownBy(() -> adminService.resetPassword(2L, pwRequest(null, "NewBoaz1234!"), currentAdmin, PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-006 존재하지 않는 계정 → ADMIN_NOT_FOUND")
        void notFound() {
            when(adminRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.resetPassword(999L, pwRequest(null, "NewBoaz1234!"), master(1L), PERMISSIONS))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_NOT_FOUND);
        }
    }
}
