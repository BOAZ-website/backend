package com.boaz.backend.domain.admin.service;

import com.boaz.backend.domain.admin.dto.request.AdminCreateRequest;
import com.boaz.backend.domain.admin.dto.request.AdminPasswordResetRequest;
import com.boaz.backend.domain.admin.dto.request.AdminUpdateRequest;
import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.dto.response.AdminIdResponse;
import com.boaz.backend.domain.admin.dto.response.AdminMeResponse;
import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.global.common.enums.AccountType;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @InjectMocks AdminService adminService;

    @Mock AdminRepository adminRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenRepository refreshTokenRepository;

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

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
    // ADMIN-001: getAccounts (모든 계정 조회, SUPER only)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-001 getAccounts")
    class GetAccounts {

        @Test
        @DisplayName("TC-001 SUPER 조회 → created_at 오름차순 목록 매핑")
        void superList() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(admin(1L, Admin.Role.SUPER), admin(2L, Admin.Role.TEAM)));

            List<AdminAccountResponse> result = adminService.getAccounts(currentAdmin);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AdminAccountResponse::getId).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("TC-002 계정 없음 → 빈 배열 (예외 없음)")
        void empty() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());

            assertThat(adminService.getAccounts(currentAdmin)).isEmpty();
        }

        @Test
        @DisplayName("TC-003 TEAM 호출 → ACCESS_DENIED (DB 조회 안 함)")
        void teamForbidden() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);

            assertThatThrownBy(() -> adminService.getAccounts(currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findAllByDeletedAtIsNullOrderByCreatedAtAsc();
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-002: createAccount (계정 생성, SUPER only)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-002 createAccount")
    class CreateAccount {

        @Test
        @DisplayName("TC-001 SUPER 생성 → 201, password 인코딩 + createdBy 기록")
        void success() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
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
        @DisplayName("TC-002 TEAM 호출 → ACCESS_DENIED, save 안 함")
        void teamForbidden() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            AdminCreateRequest req = createRequest("boaz_team2", Track.ANALYSIS);

            assertThatThrownBy(() -> adminService.createAccount(req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-003 track=ALL → INVALID_TRACK_SELECTION, save 안 함")
        void trackAll() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            AdminCreateRequest req = createRequest("boaz_team2", Track.ALL);

            assertThatThrownBy(() -> adminService.createAccount(req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
            verify(adminRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-004 username 중복 → DUPLICATE_USERNAME, save 안 함")
        void duplicateUsername() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            AdminCreateRequest req = createRequest("boaz_team2", Track.ANALYSIS);
            when(adminRepository.existsByUsernameAndDeletedAtIsNull("boaz_team2")).thenReturn(true);

            assertThatThrownBy(() -> adminService.createAccount(req, currentAdmin))
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
        @DisplayName("TC-001 SUPER 본인 정보 → id/team_name/name 매핑, repository 미상호작용")
        void superSelf() {
            Admin currentAdmin = adminWith(1L, Admin.Role.SUPER, Track.ANALYSIS, "문혁준", Admin.TeamName.대표진);

            AdminMeResponse res = adminService.getMe(currentAdmin);

            assertThat(res.getId()).isEqualTo(1L);
            assertThat(res.getName()).isEqualTo("문혁준");
            assertThat(res.getTeamName()).isEqualTo("대표진");
            verifyNoInteractions(adminRepository, refreshTokenRepository, passwordEncoder);
        }

        @Test
        @DisplayName("TC-002 TEAM 도 호출 가능 → 본인 정보 반환 (권한 거부 없음)")
        void teamAllowed() {
            Admin currentAdmin = adminWith(5L, Admin.Role.TEAM, Track.ENGINEERING, "홍길동", Admin.TeamName.기획팀);

            AdminMeResponse res = adminService.getMe(currentAdmin);

            assertThat(res.getId()).isEqualTo(5L);
            assertThat(res.getName()).isEqualTo("홍길동");
            assertThat(res.getTeamName()).isEqualTo("기획팀");
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-003: getAccount (id별 계정 조회)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-003 getAccount")
    class GetAccount {

        @Test
        @DisplayName("TC-001 SUPER 가 타 계정 조회 → 반환")
        void superOther() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(admin(2L, Admin.Role.TEAM)));

            AdminAccountResponse res = adminService.getAccount(2L, currentAdmin);

            assertThat(res.getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("TC-002 TEAM 이 본인 계정 조회 → 반환")
        void teamSelf() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(currentAdmin));

            AdminAccountResponse res = adminService.getAccount(5L, currentAdmin);

            assertThat(res.getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("TC-003 TEAM 이 타 계정 조회 → ACCESS_DENIED (DB 조회 안 함)")
        void teamOther() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);

            assertThatThrownBy(() -> adminService.getAccount(2L, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-004 존재하지 않는 계정 → ADMIN_NOT_FOUND")
        void notFound() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getAccount(999L, currentAdmin))
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
        @DisplayName("TC-001 SUPER 프로필 필드만 수정 → 변경, role 미전송 시 토큰 삭제 안 함")
        void profileOnly() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            Admin target = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "name", JsonNullable.of("새이름"));

            AdminIdResponse res = adminService.updateAccount(2L, req, currentAdmin);

            assertThat(res.getId()).isEqualTo(2L);
            assertThat(target.getName()).isEqualTo("새이름");
            verify(refreshTokenRepository, never()).deleteByAccountTypeAndAccountId(any(), anyLong());
        }

        @Test
        @DisplayName("TC-002 SUPER 가 타 계정 role 변경 → 변경 + RefreshToken 삭제")
        void roleChange() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            Admin target = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.SUPER));

            adminService.updateAccount(2L, req, currentAdmin);

            assertThat(target.getRole()).isEqualTo(Admin.Role.SUPER);
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 2L);
        }

        @Test
        @DisplayName("TC-003 TEAM 이 타 계정 수정 → ACCESS_DENIED (DB 조회 안 함)")
        void teamOther() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            AdminUpdateRequest req = new AdminUpdateRequest();

            assertThatThrownBy(() -> adminService.updateAccount(2L, req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-004 TEAM 이 본인에 role 전송 → ACCESS_DENIED (CANNOT_MODIFY_OWN_ROLE 아님, 순서 2>3)")
        void teamSelfRole() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.SUPER));

            assertThatThrownBy(() -> adminService.updateAccount(5L, req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("TC-005 SUPER 가 본인 role 변경 → CANNOT_MODIFY_OWN_ROLE (DB 조회 안 함)")
        void superSelfRole() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "role", JsonNullable.of(Admin.Role.TEAM));

            assertThatThrownBy(() -> adminService.updateAccount(1L, req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ROLE);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-006 존재하지 않는 계정 → ADMIN_NOT_FOUND")
        void notFound() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());
            AdminUpdateRequest req = new AdminUpdateRequest();

            assertThatThrownBy(() -> adminService.updateAccount(999L, req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-007 track=ALL → INVALID_TRACK_SELECTION")
        void trackAll() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(admin(2L, Admin.Role.TEAM)));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "track", JsonNullable.of(Track.ALL));

            assertThatThrownBy(() -> adminService.updateAccount(2L, req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
        }

        @Test
        @DisplayName("TC-008 term < 0 → INVALID_INPUT_VALUE")
        void negativeTerm() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(admin(2L, Admin.Role.TEAM)));
            AdminUpdateRequest req = new AdminUpdateRequest();
            ReflectionTestUtils.setField(req, "term", JsonNullable.of(-1));

            assertThatThrownBy(() -> adminService.updateAccount(2L, req, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // ──────────────────────────────────────────────
    // ADMIN-005: deleteAccount (id별 계정 삭제, soft delete)
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("ADMIN-005 deleteAccount")
    class DeleteAccount {

        @Test
        @DisplayName("TC-001 SUPER 가 타 계정(TEAM) 삭제 → soft delete + RefreshToken 삭제")
        void superDeletesOther() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            Admin target = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));

            adminService.deleteAccount(2L, currentAdmin);

            assertThat(target.isDeleted()).isTrue();
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 2L);
            // 대상이 TEAM 이므로 SUPER 카운트 조회 안 함
            verify(adminRepository, never()).countByRoleAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("TC-002 SUPER 본인 삭제 (다른 SUPER 존재) → 허용")
        void superSelfDeleteAllowed() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(currentAdmin));
            when(adminRepository.countByRoleAndDeletedAtIsNull(Admin.Role.SUPER)).thenReturn(2L);

            adminService.deleteAccount(1L, currentAdmin);

            assertThat(currentAdmin.isDeleted()).isTrue();
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 1L);
        }

        @Test
        @DisplayName("TC-003 마지막 SUPER 삭제 시도 → LAST_SUPER_ACCOUNT")
        void lastSuper() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(currentAdmin));
            when(adminRepository.countByRoleAndDeletedAtIsNull(Admin.Role.SUPER)).thenReturn(1L);

            assertThatThrownBy(() -> adminService.deleteAccount(1L, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.LAST_SUPER_ACCOUNT);
            assertThat(currentAdmin.isDeleted()).isFalse();
            verify(refreshTokenRepository, never()).deleteByAccountTypeAndAccountId(any(), anyLong());
        }

        @Test
        @DisplayName("TC-004 TEAM 이 타 계정 삭제 → ACCESS_DENIED (DB 조회 안 함)")
        void teamOther() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);

            assertThatThrownBy(() -> adminService.deleteAccount(2L, currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-005 존재하지 않는 계정 → ADMIN_NOT_FOUND")
        void notFound() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.deleteAccount(999L, currentAdmin))
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
        @DisplayName("TC-001 SUPER 타인 초기화 (currentPassword 불필요) → 변경 + 토큰 삭제, matches 미호출")
        void superResetsOther() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            Admin target = admin(2L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
            when(passwordEncoder.encode("NewBoaz1234!")).thenReturn("ENC2");

            adminService.resetPassword(2L, pwRequest(null, "NewBoaz1234!"), currentAdmin);

            assertThat(target.getPassword()).isEqualTo("ENC2");
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 2L);
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("TC-002 본인 변경 (currentPassword 일치) → 변경 + 토큰 삭제")
        void selfChangeMatches() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);
            when(adminRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(currentAdmin));
            when(passwordEncoder.matches("old", "OLD_HASH")).thenReturn(true);
            when(passwordEncoder.encode("NewBoaz1234!")).thenReturn("ENC2");

            adminService.resetPassword(5L, pwRequest("old", "NewBoaz1234!"), currentAdmin);

            assertThat(currentAdmin.getPassword()).isEqualTo("ENC2");
            verify(refreshTokenRepository).deleteByAccountTypeAndAccountId(AccountType.ADMIN, 5L);
        }

        @Test
        @DisplayName("TC-003 본인 변경인데 currentPassword 누락 → INVALID_INPUT_VALUE")
        void selfCurrentPasswordBlank() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(currentAdmin));

            assertThatThrownBy(() -> adminService.resetPassword(1L, pwRequest(null, "NewBoaz1234!"), currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            verify(refreshTokenRepository, never()).deleteByAccountTypeAndAccountId(any(), anyLong());
        }

        @Test
        @DisplayName("TC-004 본인 변경인데 currentPassword 불일치 → INVALID_CURRENT_PASSWORD")
        void selfCurrentPasswordMismatch() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(currentAdmin));
            when(passwordEncoder.matches("wrong", "OLD_HASH")).thenReturn(false);

            assertThatThrownBy(() -> adminService.resetPassword(1L, pwRequest("wrong", "NewBoaz1234!"), currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);
            verify(refreshTokenRepository, never()).deleteByAccountTypeAndAccountId(any(), anyLong());
        }

        @Test
        @DisplayName("TC-005 TEAM 이 타 계정 변경 → ACCESS_DENIED (DB 조회 안 함)")
        void teamOther() {
            Admin currentAdmin = admin(5L, Admin.Role.TEAM);

            assertThatThrownBy(() -> adminService.resetPassword(2L, pwRequest(null, "NewBoaz1234!"), currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
            verify(adminRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("TC-006 존재하지 않는 계정 → ADMIN_NOT_FOUND")
        void notFound() {
            Admin currentAdmin = admin(1L, Admin.Role.SUPER);
            when(adminRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.resetPassword(999L, pwRequest(null, "NewBoaz1234!"), currentAdmin))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_NOT_FOUND);
        }
    }
}
