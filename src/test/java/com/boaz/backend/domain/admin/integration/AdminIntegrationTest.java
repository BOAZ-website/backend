package com.boaz.backend.domain.admin.integration;

import com.boaz.backend.domain.admin.dto.request.AdminCreateRequest;
import com.boaz.backend.domain.admin.dto.request.AdminPasswordResetRequest;
import com.boaz.backend.domain.admin.dto.request.AdminUpdateRequest;
import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.admin.service.AdminService;
import com.boaz.backend.domain.auth.entity.RefreshToken;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.global.common.enums.AccountType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class AdminIntegrationTest extends TestcontainersBase {

    @Autowired AdminService adminService;
    @Autowired AdminRepository adminRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @PersistenceContext EntityManager em;

    private int seq = 0;

    private Admin saveAdmin(Admin.Role role, String rawPassword) {
        Admin a = Admin.builder()
                .username("user" + (++seq)).password(passwordEncoder.encode(rawPassword)).role(role)
                .name("name" + seq).track(Track.ANALYSIS).term(25).teamName(Admin.TeamName.기획팀).createdBy(null)
                .build();
        return adminRepository.save(a);
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
            Admin superAdmin = saveAdmin(Admin.Role.SUPER, "Super1234!");
            em.flush();
            em.clear();

            var res = adminService.createAccount(createReq("new_team"), superAdmin);
            em.flush();
            em.clear();

            Admin saved = adminRepository.findById(res.getId()).orElseThrow();
            assertThat(saved.getPassword()).isNotEqualTo("Boaz1234!");
            assertThat(passwordEncoder.matches("Boaz1234!", saved.getPassword())).isTrue();
            assertThat(saved.getCreatedBy()).isEqualTo(superAdmin.getId());
        }

        @Test
        @DisplayName("동일 username 재생성 → DUPLICATE_USERNAME")
        void duplicateUsername() {
            Admin superAdmin = saveAdmin(Admin.Role.SUPER, "Super1234!");
            adminService.createAccount(createReq("dup_team"), superAdmin);
            em.flush();
            em.clear();

            assertThatThrownBy(() -> adminService.createAccount(createReq("dup_team"), superAdmin))
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
            Admin superAdmin = saveAdmin(Admin.Role.SUPER, "Super1234!");
            Admin target = saveAdmin(Admin.Role.TEAM, "Team1234!");
            saveRefreshToken(target.getId());
            em.flush();
            em.clear();

            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.SUPER));
            adminService.updateAccount(target.getId(), req, superAdmin);
            em.flush();
            em.clear();

            assertThat(adminRepository.findById(target.getId()).orElseThrow().getRole())
                    .isEqualTo(Admin.Role.SUPER);
            assertThat(refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.ADMIN, target.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("프로필 필드만 수정 → 대상 RefreshToken 유지")
        void profileOnlyKeepsToken() {
            Admin superAdmin = saveAdmin(Admin.Role.SUPER, "Super1234!");
            Admin target = saveAdmin(Admin.Role.TEAM, "Team1234!");
            saveRefreshToken(target.getId());
            em.flush();
            em.clear();

            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "name", JsonNullable.of("변경된이름"));
            adminService.updateAccount(target.getId(), req, superAdmin);
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
            Admin superAdmin = saveAdmin(Admin.Role.SUPER, "Super1234!");
            Admin target = saveAdmin(Admin.Role.TEAM, "Team1234!");
            saveRefreshToken(target.getId());
            em.flush();
            em.clear();

            adminService.deleteAccount(target.getId(), superAdmin);
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
        @DisplayName("마지막 SUPER 삭제 → LAST_SUPER_ACCOUNT (삭제 안 됨)")
        void lastSuperBlocked() {
            Admin superAdmin = saveAdmin(Admin.Role.SUPER, "Super1234!");
            em.flush();
            em.clear();

            assertThatThrownBy(() -> adminService.deleteAccount(superAdmin.getId(), superAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.LAST_SUPER_ACCOUNT);
            assertThat(adminRepository.findByIdAndDeletedAtIsNull(superAdmin.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 end-to-end (ADMIN-006)")
    class ResetPassword {

        @Test
        @DisplayName("SUPER 타인 초기화 → 해시 변경(matches new) + RefreshToken 삭제")
        void superResetsOther() {
            Admin superAdmin = saveAdmin(Admin.Role.SUPER, "Super1234!");
            Admin target = saveAdmin(Admin.Role.TEAM, "Team1234!");
            saveRefreshToken(target.getId());
            em.flush();
            em.clear();

            AdminPasswordResetRequest req = new AdminPasswordResetRequest();
            ReflectionTestUtils.setField(req, "newPassword", "NewBoaz1234!");
            adminService.resetPassword(target.getId(), req, superAdmin);
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
            Admin self = saveAdmin(Admin.Role.TEAM, "Team1234!");
            saveRefreshToken(self.getId());
            em.flush();
            em.clear();

            Admin currentAdmin = adminRepository.findById(self.getId()).orElseThrow();
            AdminPasswordResetRequest req = new AdminPasswordResetRequest();
            ReflectionTestUtils.setField(req, "currentPassword", "Team1234!");
            ReflectionTestUtils.setField(req, "newPassword", "NewBoaz1234!");
            adminService.resetPassword(self.getId(), req, currentAdmin);
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
            Admin superAdmin = saveAdmin(Admin.Role.SUPER, "Super1234!");
            Admin active = saveAdmin(Admin.Role.TEAM, "Team1234!");
            Admin deleted = saveAdmin(Admin.Role.TEAM, "Team1234!");
            deleted.softDelete();
            em.flush();
            em.clear();

            var result = adminService.getAccounts(superAdmin);

            assertThat(result).extracting("id")
                    .contains(superAdmin.getId(), active.getId())
                    .doesNotContain(deleted.getId());
        }
    }
}
