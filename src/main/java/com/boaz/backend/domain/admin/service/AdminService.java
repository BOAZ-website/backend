package com.boaz.backend.domain.admin.service;

import com.boaz.backend.domain.admin.dto.request.AdminCreateRequest;
import com.boaz.backend.domain.admin.dto.request.AdminPasswordResetRequest;
import com.boaz.backend.domain.admin.dto.request.AdminUpdateRequest;
import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.dto.response.AdminIdResponse;
import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
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
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        return adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()
                .stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public AdminIdResponse createAccount(AdminCreateRequest request, Admin currentAdmin) {
        if (currentAdmin.getRole() != Admin.Role.SUPER) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
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

    public AdminAccountResponse getAccount(Long id, Admin currentAdmin) {
        if (currentAdmin.getRole() == Admin.Role.TEAM && !currentAdmin.getId().equals(id)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        return AdminAccountResponse.from(admin);
    }

    @Transactional
    public AdminIdResponse updateAccount(Long id, AdminUpdateRequest request, Admin currentAdmin) {
        // TEAM은 본인 계정만 수정 가능
        if (currentAdmin.getRole() == Admin.Role.TEAM && !currentAdmin.getId().equals(id)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        // TEAM은 role 변경 불가
        if (currentAdmin.getRole() == Admin.Role.TEAM && request.getRole().isPresent()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        // 본인 role 변경 불가
        if (request.getRole().isPresent() && currentAdmin.getId().equals(id)) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_OWN_ROLE);
        }

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        request.getTrack().ifPresent(Track::validateNotAll);

        request.getRole().ifPresent(role -> {
            admin.updateRole(role);
            refreshTokenRepository.deleteByAdminId(id);
        });

        request.getName().ifPresent(admin::updateName);
        request.getTrack().ifPresent(admin::updateTrack);
        request.getTerm().ifPresent(admin::updateTerm);
        request.getTeamName().ifPresent(admin::updateTeamName);

        return new AdminIdResponse(admin.getId());
    }

    @Transactional
    public void deleteAccount(Long id, Admin currentAdmin) {
        if (currentAdmin.getRole() == Admin.Role.TEAM && !currentAdmin.getId().equals(id)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        if (admin.getRole() == Admin.Role.SUPER
                && adminRepository.countByRoleAndDeletedAtIsNull(Admin.Role.SUPER) <= 1) {
            throw new CustomException(ErrorCode.LAST_SUPER_ACCOUNT);
        }

        admin.softDelete();
        refreshTokenRepository.deleteByAdminId(id);
    }

    @Transactional
    public void resetPassword(Long id, AdminPasswordResetRequest request, Admin currentAdmin) {
        if (currentAdmin.getRole() == Admin.Role.TEAM && !currentAdmin.getId().equals(id)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        admin.resetPassword(passwordEncoder.encode(request.getNewPassword()));
        refreshTokenRepository.deleteByAdminId(id);
    }
}
