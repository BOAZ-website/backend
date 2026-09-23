package com.boaz.backend.domain.admin.integration;

import com.boaz.backend.domain.admin.dto.request.AdminCreateRequest;
import com.boaz.backend.domain.admin.dto.request.AdminPasswordResetRequest;
import com.boaz.backend.domain.admin.dto.request.AdminUpdateRequest;
import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.entity.AdminPermissionOverride;
import com.boaz.backend.domain.admin.repository.AdminPermissionOverrideRepository;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.admin.service.AdminService;
import com.boaz.backend.domain.auth.entity.RefreshToken;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.global.common.enums.AccountType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.authz.EffectivePermissions;
import com.boaz.backend.global.security.authz.Permission;
import com.boaz.backend.support.TestcontainersBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class AdminIntegrationTest extends TestcontainersBase {

    @Autowired AdminService adminService;
    @Autowired AdminRepository adminRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AdminPermissionOverrideRepository overrideRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EffectivePermissions effectivePermissions;

    @PersistenceContext EntityManager em;

    private int seq = 0;

    /** 권한 키는 {@code (role, teamName)} 이라 role 만으로는 주체가 정해지지 않는다. */
    /**
     * 운영과 같은 경로로 유효 권한을 계산한다. 컨트롤러가 {@code AdminUserDetails} 에서 꺼내
     * 서비스로 넘기는 값과 같아야 3층 판정이 실제와 같은 입력을 받는다.
     */
    private Set<Permission> permissionsOf(Admin admin) {
        return effectivePermissions.of(admin);
    }

    private Admin saveAdmin(Admin.Role role, Admin.TeamName teamName, String rawPassword) {
        Admin a = Admin.builder()
                .username("user" + (++seq)).password(passwordEncoder.encode(rawPassword)).role(role)
                .name("name" + seq).track(Track.ANALYSIS).term(25).teamName(teamName).createdBy(null)
                .build();
        return adminRepository.save(a);
    }

    /** 계정 CRUD 권한을 다 가진 유일한 키. 옛 테스트의 SUPER 주체 자리를 대신한다. */
    private Admin saveMaster(String rawPassword) {
        return saveAdmin(Admin.Role.MASTER, Admin.TeamName.서비스운영팀, rawPassword);
    }

    private void saveRefreshToken(Long adminId) {
        refreshTokenRepository.save(RefreshToken.builder()
                .accountId(adminId).accountType(AccountType.ADMIN)
                .token("token-" + adminId).expiresAt(LocalDateTime.now().plusDays(1))
                .build());
    }

    private AdminCreateRequest createReq(String username) {
        AdminCreateRequest req = new AdminCreateRequest();
        ReflectionTestUtils.setField(req, "username", username);
        ReflectionTestUtils.setField(req, "password", "Boaz1234!");
        ReflectionTestUtils.setField(req, "role", Admin.Role.TEAM);
        ReflectionTestUtils.setField(req, "name", "김보아즈");
        ReflectionTestUtils.setField(req, "track", Track.ANALYSIS);
        ReflectionTestUtils.setField(req, "term", 25);
        ReflectionTestUtils.setField(req, "teamName", Admin.TeamName.기획팀);
        return req;
    }

    @Nested
    @DisplayName("계정 생성 end-to-end (ADMIN-002)")
    class CreateAccount {

        @Test
        @DisplayName("생성 → DB 영속, password BCrypt 해시, createdBy = 생성자 id")
        void persistsWithHashedPassword() {
            Admin master = saveMaster("Master1234!");
            em.flush();
            em.clear();

            var res = adminService.createAccount(createReq("new_team"), master);
            em.flush();
            em.clear();

            Admin saved = adminRepository.findById(res.getId()).orElseThrow();
            assertThat(saved.getPassword()).isNotEqualTo("Boaz1234!");
            assertThat(passwordEncoder.matches("Boaz1234!", saved.getPassword())).isTrue();
            assertThat(saved.getCreatedBy()).isEqualTo(master.getId());
        }

        @Test
        @DisplayName("동일 username 재생성 → DUPLICATE_USERNAME")
        void duplicateUsername() {
            Admin master = saveMaster("Master1234!");
            adminService.createAccount(createReq("dup_team"), master);
            em.flush();
            em.clear();

            assertThatThrownBy(() -> adminService.createAccount(createReq("dup_team"), master))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_USERNAME);
        }
    }

    @Nested
    @DisplayName("계정 수정 end-to-end (ADMIN-004)")
    class UpdateAccount {

        @Test
        @DisplayName("role 변경 → 영속 반영 + 대상 RefreshToken 삭제")
        void roleChangeDeletesToken() {
            Admin master = saveMaster("Master1234!");
            Admin target = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            saveRefreshToken(target.getId());
            em.flush();
            em.clear();

            AdminUpdateRequest req = new AdminUpdateRequest();
            // saveAdmin 의 기본 소속은 기획팀이다. role 만 SUPER 로 올리면 (SUPER, 기획팀) 이라
            // 매트릭스에 없는 조합이 되므로 실제 승격처럼 소속도 함께 바꾼다.
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.SUPER));
            ReflectionTestUtils.setField(req, "teamName", JsonNullable.of(Admin.TeamName.대표진));
            adminService.updateAccount(target.getId(), req, master, permissionsOf(master));
            em.flush();
            em.clear();

            assertThat(adminRepository.findById(target.getId()).orElseThrow().getRole())
                    .isEqualTo(Admin.Role.SUPER);
            assertThat(adminRepository.findById(target.getId()).orElseThrow().getTeamName())
                    .isEqualTo(Admin.TeamName.대표진);
            assertThat(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.ADMIN, target.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("role 변경 → 그 계정의 오버라이드 행 전삭제 (base 가 바뀌면 차이도 무의미해진다)")
        void keyChangeClearsOverrides() {
            Admin master = saveMaster("Master1234!");
            Admin target = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            overrideRepository.save(AdminPermissionOverride.builder()
                    .adminId(target.getId())
                    .permission(Permission.CONTENT_WRITE)
                    .effect(AdminPermissionOverride.Effect.GRANT)
                    .grantedBy(master.getId())
                    .build());
            em.flush();
            em.clear();

            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "teamName", JsonNullable.of(Admin.TeamName.운영지원팀));
            adminService.updateAccount(target.getId(), req, master, permissionsOf(master));
            em.flush();
            em.clear();

            assertThat(overrideRepository.findByAdminId(target.getId())).isEmpty();
        }

        @Test
        @DisplayName("프로필 필드만 수정 → 대상 RefreshToken 유지")
        void profileOnlyKeepsToken() {
            Admin master = saveMaster("Master1234!");
            Admin target = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            saveRefreshToken(target.getId());
            em.flush();
            em.clear();

            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "name", JsonNullable.of("변경된이름"));
            adminService.updateAccount(target.getId(), req, master, permissionsOf(master));
            em.flush();
            em.clear();

            assertThat(adminRepository.findById(target.getId()).orElseThrow().getName())
                    .isEqualTo("변경된이름");
            assertThat(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.ADMIN, target.getId()))
                    .isPresent();
        }
    }

    @Nested
    @DisplayName("계정 삭제 end-to-end (ADMIN-005)")
    class DeleteAccount {

        @Test
        @DisplayName("soft delete → deletedAt 세팅, 목록 제외, RefreshToken 삭제")
        void softDeleteExcludesAndDeletesToken() {
            Admin master = saveMaster("Master1234!");
            Admin target = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            saveRefreshToken(target.getId());
            em.flush();
            em.clear();

            adminService.deleteAccount(target.getId());
            em.flush();
            em.clear();

            // 레코드는 물리 존재하지만 deletedAt 세팅됨
            Admin reloaded = adminRepository.findById(target.getId()).orElseThrow();
            assertThat(reloaded.isDeleted()).isTrue();
            // 활성 목록(findAll...DeletedAtIsNull)에서 제외
            assertThat(adminRepository.findByIdAndDeletedAtIsNull(target.getId())).isEmpty();
            assertThat(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc())
                    .extracting(Admin::getId).doesNotContain(target.getId());
            // RefreshToken 삭제
            assertThat(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.ADMIN, target.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("마지막 계정 관리자 삭제 → LAST_ACCOUNT_MANAGER (삭제 안 됨)")
        void lastAccountManagerBlocked() {
            Admin master = saveMaster("Master1234!");
            saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            em.flush();
            em.clear();

            // 다른 계정이 있어도 ADMIN_ACCOUNT_CREATE_DELETE 를 가진 계정이 이것뿐이면 막힌다 —
            // 기준이 계정 수가 아니라 유효 권한 보유자 수다.
            assertThatThrownBy(() -> adminService.deleteAccount(master.getId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.LAST_ACCOUNT_MANAGER);
            assertThat(adminRepository.findByIdAndDeletedAtIsNull(master.getId())).isPresent();
        }

        @Test
        @DisplayName("오버라이드로 권한을 받은 계정이 있으면 마지막 관리자도 삭제된다 — role 로 세지 않는다")
        void grantedByOverrideCountsAsManager() {
            Admin master = saveMaster("Master1234!");
            Admin granted = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            overrideRepository.save(AdminPermissionOverride.builder()
                    .adminId(granted.getId())
                    .permission(Permission.ADMIN_ACCOUNT_CREATE_DELETE)
                    .effect(AdminPermissionOverride.Effect.GRANT)
                    .grantedBy(master.getId())
                    .build());
            em.flush();
            em.clear();

            adminService.deleteAccount(master.getId());
            em.flush();
            em.clear();

            assertThat(adminRepository.findByIdAndDeletedAtIsNull(master.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 end-to-end (ADMIN-006)")
    class ResetPassword {

        @Test
        @DisplayName("계정 관리자의 타인 초기화 → 해시 변경(matches new) + RefreshToken 삭제")
        void superResetsOther() {
            Admin master = saveMaster("Master1234!");
            Admin target = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            saveRefreshToken(target.getId());
            em.flush();
            em.clear();

            AdminPasswordResetRequest req = new AdminPasswordResetRequest();
            ReflectionTestUtils.setField(req, "newPassword", "NewBoaz1234!");
            adminService.resetPassword(target.getId(), req, master, permissionsOf(master));
            em.flush();
            em.clear();

            Admin reloaded = adminRepository.findById(target.getId()).orElseThrow();
            assertThat(passwordEncoder.matches("NewBoaz1234!", reloaded.getPassword())).isTrue();
            assertThat(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.ADMIN, target.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("본인 변경 (currentPassword 일치) → 해시 변경")
        void selfChange() {
            Admin self = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            saveRefreshToken(self.getId());
            em.flush();
            em.clear();

            Admin currentAdmin = adminRepository.findById(self.getId()).orElseThrow();
            AdminPasswordResetRequest req = new AdminPasswordResetRequest();
            ReflectionTestUtils.setField(req, "currentPassword", "Team1234!");
            ReflectionTestUtils.setField(req, "newPassword", "NewBoaz1234!");
            adminService.resetPassword(self.getId(), req, currentAdmin, permissionsOf(currentAdmin));
            em.flush();
            em.clear();

            assertThat(passwordEncoder.matches("NewBoaz1234!",
                    adminRepository.findById(self.getId()).orElseThrow().getPassword())).isTrue();
        }
    }

    @Nested
    @DisplayName("모든 계정 조회 end-to-end (ADMIN-001)")
    class GetAccounts {

        @Test
        @DisplayName("soft delete 제외하고 활성 계정만 반환")
        void excludesDeleted() {
            Admin master = saveMaster("Master1234!");
            Admin active = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            Admin deleted = saveAdmin(Admin.Role.TEAM, Admin.TeamName.기획팀, "Team1234!");
            deleted.softDelete();
            em.flush();
            em.clear();

            var result = adminService.getAccounts();

            assertThat(result).extracting("id")
                    .contains(master.getId(), active.getId())
                    .doesNotContain(deleted.getId());
        }
    }
}
