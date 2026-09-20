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
import com.boaz.backend.global.security.authz.DefaultPermissions;
import com.boaz.backend.global.security.authz.EffectivePermissions;
import com.boaz.backend.global.security.authz.Permission;
import com.boaz.backend.global.security.authz.ScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * <b>인가 판정이 이 클래스에 없다.</b> permission 보유 여부는 2층({@code AdminController}의
 * {@code @PreAuthorize})이, 대상이 본인이냐 타인이냐는 3층({@link ScopeGuard})이 본다.
 * 여기 남은 것은 락아웃 방지처럼 <b>대상을 저장 직전에 다시 세어야만</b> 판정되는 것뿐이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminPermissionOverrideRepository overrideRepository;
    private final EffectivePermissions effectivePermissions;
    private final ScopeGuard scopeGuard;

    /** 주체를 안 받는다 — 대상 판정이 없는 행이라 2층({@code ADMIN_ACCOUNT_READ})에서 끝난다. */
    public List<AdminAccountResponse> getAccounts() {
        return adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()
                .stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public AdminIdResponse createAccount(AdminCreateRequest request, Admin currentAdmin) {
        request.getTrack().validateNotAll();
        validateRoleTeam(request.getRole(), request.getTeamName());

        if (adminRepository.existsByUsernameAndDeletedAtIsNull(request.getUsername())) {
            throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
        }

        Admin admin = Admin.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .name(request.getName())
                .track(request.getTrack())
                .term(request.getTerm())
                .teamName(request.getTeamName())
                .createdBy(currentAdmin.getId())
                .build();

        adminRepository.save(admin);
        return new AdminIdResponse(admin.getId());
    }

    public AdminMeResponse getMe(Admin currentAdmin) {
        return AdminMeResponse.from(currentAdmin);
    }

    public AdminAccountResponse getAccount(Long id, Admin currentAdmin, Set<Permission> currentPermissions) {
        scopeGuard.checkAccountRead(currentAdmin, currentPermissions, id);

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        return AdminAccountResponse.from(admin);
    }

    @Transactional
    public AdminIdResponse updateAccount(Long id, AdminUpdateRequest request, Admin currentAdmin,
                                         Set<Permission> currentPermissions) {
        scopeGuard.checkAccountWrite(currentAdmin, currentPermissions, id);

        // role·teamName 이 기본 권한 세트의 키다. 본인 키를 스스로 바꾸는 것은 금지이다. 
        if (currentAdmin.getId().equals(id) && isKeyChange(currentAdmin, request)) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_OWN_ROLE);
        }

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        request.getTrack().ifPresent(Track::validateNotAll);
        request.getTerm().ifPresent(t -> {
            if (t < 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        });
        if (isKeyChange(admin, request)) {
            // 부분 업데이트다. 한쪽만 와도 결과는 둘의 조합이라 변경 후 값끼리 합쳐서 본다.
            // 키를 안 건드리는 요청(이름·기수만 수정)까지 검사하지는 않는다.
            Admin.Role newRole = request.getRole().orElse(admin.getRole());
            Admin.TeamName newTeam = request.getTeamName().orElse(admin.getTeamName());

            // 락아웃 검사보다 먼저다. 무효 조합이면 DefaultPermissions.of 가 빈 집합을 주므로,
            // 순서가 뒤집히면 조합이 틀린 요청이 LAST_ACCOUNT_MANAGER 로 잘못 보고된다.
            validateRoleTeam(newRole, newTeam);
            guardAccountManagerRemains(admin, DefaultPermissions.of(newRole, newTeam));

            // 오버라이드는 절대 목록이 아니라 base 대비 차이다. 키가 바뀌면 기준이 달라지므로
            // 남겨 두면 조용히 다른 권한이 된다.
            overrideRepository.deleteByAdminId(id);
            // 재로그인 강제. role 뿐 아니라 teamName 도 권한 키의 절반이라 같이 건다.
            refreshTokenRepository.deleteByAccountTypeAndAccountId(AccountType.ADMIN, id);
        }

        admin.update(
                request.getRole().orElse(null),
                request.getName().orElse(null),
                request.getTrack().orElse(null),
                request.getTerm().orElse(null),
                request.getTeamName().orElse(null)
        );

        return new AdminIdResponse(admin.getId());
    }

    @Transactional
    /** 주체를 안 받는다 — 2층이 {@code ADMIN_ACCOUNT_CREATE_DELETE} 를 보고, 락아웃 검사는 대상만 본다. */
    public void deleteAccount(Long id) {
        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        guardAccountManagerRemains(admin, Set.of());

        admin.softDelete();
        refreshTokenRepository.deleteByAccountTypeAndAccountId(AccountType.ADMIN, id);
    }

    @Transactional
    public void resetPassword(Long id, AdminPasswordResetRequest request, Admin currentAdmin,
                              Set<Permission> currentPermissions) {
        scopeGuard.checkAccountWrite(currentAdmin, currentPermissions, id);

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        // 본인 비밀번호 변경에는 현재 비밀번호를 요구한다. 타인 초기화(ADMIN_ACCOUNT_WRITE)는 면제 —
        // 남의 현재 비밀번호를 알 수 없어서다.
        if (currentAdmin.getId().equals(id)) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), admin.getPassword())) {
                throw new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD);
            }
        }

        admin.resetPassword(passwordEncoder.encode(request.getNewPassword()));
        refreshTokenRepository.deleteByAccountTypeAndAccountId(AccountType.ADMIN, id);
    }

    /**
     * 키가 실제로 달라질 때만 true. 같은 값으로 들어온 요청은 키 변경이 아니고, 부재는 orElse 가 흡수한다.
     *
     * <p>본인 검사(DB 조회 전, 대상이 {@code currentAdmin})와 정리 로직의 진입 조건이 이 하나를 공유한다 —
     * 여기서 갈라지면 "자기 키는 못 바꾸는데 남의 계정은 정리 없이 통과" 같은 불일치가 조용히 생긴다.
     * 유도 방식은 블록 안의 {@code newRole}·{@code newTeam} 과 같아야 한다.
     */
    private boolean isKeyChange(Admin admin, AdminUpdateRequest request) {
        return request.getRole().orElse(admin.getRole()) != admin.getRole()
                || request.getTeamName().orElse(admin.getTeamName()) != admin.getTeamName();
    }

    /**
     * 권한 매트릭스에 있는 (role, teamName) 조합만 저장한다.
     *
     * <p>{@code DefaultPermissions.of}가 없는 조합에 권한 0을 주는 것은 <b>읽기 쪽 방어</b>일 뿐이다.
     * 저장까지 허용하면 어느 매트릭스 열에도 해당하지 않는 계정이 생기고, 그 계정은 로그인은 되는데
     * 본인 계정 조회조차 막히는 상태가 된다 — 원인을 추적하기 어려운 실패라 입력에서 거절한다.
     */
    private void validateRoleTeam(Admin.Role role, Admin.TeamName teamName) {
        if (!DefaultPermissions.isValidCombination(role, teamName)) {
            throw new CustomException(ErrorCode.INVALID_ROLE_TEAM_COMBINATION);
        }
    }

    /**
     * 락아웃 방지 — {@code ADMIN_ACCOUNT_CREATE_DELETE} 보유자가 0이 되는 변경을 거부한다.
     * 
     * <p>기준이 role 이 아닌 이유: {@code role = MASTER} 인 계정 수를 세면,
     * 생성 권한이 REVOKE 된 {@code MASTER} 는 role 카운트를 통과하지만 아무것도 못 만든다.
     *
     * @param target             바뀌는(또는 삭제되는) 계정
     * @param targetPermissions  변경 후 그 계정이 갖게 될 권한. 삭제면 빈 집합이다
     */
    private void guardAccountManagerRemains(Admin target, Set<Permission> targetPermissions) {
        boolean losesCreateDelete =
                effectivePermissions.of(target).contains(Permission.ADMIN_ACCOUNT_CREATE_DELETE)
                        && !targetPermissions.contains(Permission.ADMIN_ACCOUNT_CREATE_DELETE);
        if (!losesCreateDelete) {
            return;
        }

        List<Admin> others = adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc().stream()
                .filter(a -> !a.getId().equals(target.getId()))
                .toList();

        // 오버라이드 조회 1회로 전원을 계산한다. 계정마다 of(admin) 를 부르면 계정 수만큼 쿼리가 나간다.
        boolean anotherRemains = effectivePermissions.of(others).values().stream()
                .anyMatch(p -> p.contains(Permission.ADMIN_ACCOUNT_CREATE_DELETE));

        if (!anotherRemains) {
            throw new CustomException(ErrorCode.LAST_ACCOUNT_MANAGER);
        }
    }
}
