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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    public List<AdminAccountResponse> getAccounts(Admin currentAdmin) {
        if (currentAdmin.getRole() != Admin.Role.SUPER) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        return adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()
                .stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public AdminIdResponse createAccount(AdminCreateRequest request, Admin currentAdmin) {
        if (currentAdmin.getRole() != Admin.Role.SUPER) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        request.getTrack().validateNotAll();

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

    public AdminAccountResponse getAccount(Long id, Admin currentAdmin) {
        boolean isSelf = currentAdmin.getId().equals(id);
        boolean isTeam = currentAdmin.getRole() == Admin.Role.TEAM;
        if (isTeam && !isSelf) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        return AdminAccountResponse.from(admin);
    }

    @Transactional
    public AdminIdResponse updateAccount(Long id, AdminUpdateRequest request, Admin currentAdmin) {
        boolean isSelf = currentAdmin.getId().equals(id);
        boolean isTeam = currentAdmin.getRole() == Admin.Role.TEAM;

        if (isTeam && !isSelf) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (isTeam && request.getRole().isPresent()) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (isSelf && request.getRole().isPresent()) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_OWN_ROLE);
        }

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        request.getTrack().ifPresent(Track::validateNotAll);
        request.getTerm().ifPresent(t -> {
            if (t < 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        });

        if (request.getRole().isPresent()) {
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
    public void deleteAccount(Long id, Admin currentAdmin) {
        boolean isSelf = currentAdmin.getId().equals(id);
        boolean isTeam = currentAdmin.getRole() == Admin.Role.TEAM;
        if (isTeam && !isSelf) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        if (admin.getRole() == Admin.Role.SUPER
                && adminRepository.countByRoleAndDeletedAtIsNull(Admin.Role.SUPER) <= 1) {
            throw new CustomException(ErrorCode.LAST_SUPER_ACCOUNT);
        }

        admin.softDelete();
        refreshTokenRepository.deleteByAccountTypeAndAccountId(AccountType.ADMIN, id);
    }

    @Transactional
    public void resetPassword(Long id, AdminPasswordResetRequest request, Admin currentAdmin) {
        boolean isSelf = currentAdmin.getId().equals(id);
        boolean isTeam = currentAdmin.getRole() == Admin.Role.TEAM;
        if (isTeam && !isSelf) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
        if (isSelf) {
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
}
