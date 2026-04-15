package com.boaz.backend.domain.admin.service;

import com.boaz.backend.domain.admin.dto.request.AdminCreateRequest;
import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.dto.response.AdminIdResponse;
import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
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
}
