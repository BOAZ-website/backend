package com.boaz.backend.domain.admin.service;

import com.boaz.backend.domain.admin.dto.response.AdminAccountResponse;
import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRepository adminRepository;

    public List<AdminAccountResponse> getAccounts(Admin currentAdmin) {
        if (currentAdmin.getRole() != Admin.Role.SUPER) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        return adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()
                .stream()
                .map(AdminAccountResponse::from)
                .toList();
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
