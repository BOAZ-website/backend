package com.boaz.backend.global.security;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.authz.EffectivePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final EffectivePermissions effectivePermissions;

    /**
     * 매 요청 호출된다(JWT에 권한을 싣지 않기 때문). 늘어나는 DB 왕복은 오버라이드 조회 1회.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = adminRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
        return new AdminUserDetails(admin, effectivePermissions.of(admin));
    }
}