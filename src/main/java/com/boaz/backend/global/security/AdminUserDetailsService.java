package com.boaz.backend.global.security;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.authz.EffectivePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * 관리자 Access Token의 계정을 찾는다. {@link JwtAuthenticationFilter}만 쓴다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService {

    private final AdminRepository adminRepository;
    private final EffectivePermissions effectivePermissions;

    /**
     * 매 요청 호출된다(JWT에 권한을 싣지 않기 때문). 늘어나는 DB 왕복은 오버라이드 조회 1회.
     *
     * @throws CustomException 없거나 삭제된 계정이면 401 {@code INVALID_TOKEN}. 계정이 사라진 토큰은
     *                         유효하지 않은 토큰으로 본다.
     */
    public UserDetails loadUserByAdminId(Long adminId) {
        Admin admin = adminRepository.findByIdAndDeletedAtIsNull(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));
        return new AdminUserDetails(admin, effectivePermissions.of(admin));
    }
}
