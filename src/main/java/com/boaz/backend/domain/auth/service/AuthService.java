package com.boaz.backend.domain.auth.service;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.auth.dto.request.LoginRequest;
import com.boaz.backend.domain.auth.dto.response.LoginResponse;
import com.boaz.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.boaz.backend.domain.auth.entity.RefreshToken;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.global.common.enums.AccountType;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminRepository adminRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse adminLogin(LoginRequest request) {
        Admin admin = adminRepository.findByUsernameAndDeletedAtIsNull(request.getUsername())
                .filter(a -> passwordEncoder.matches(request.getPassword(), a.getPassword()))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        String accessToken = jwtProvider.generateAdminAccessToken(
                admin.getId(), admin.getUsername(), admin.getRole().name());
        String newRefreshToken = jwtProvider.generateAdminRefreshToken(admin.getId());

        refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.ADMIN, admin.getId())
                .ifPresentOrElse(
                        rt -> rt.update(newRefreshToken, jwtProvider.getRefreshTokenExpiry()),
                        () -> refreshTokenRepository.save(RefreshToken.builder()
                                .accountType(AccountType.ADMIN)
                                .accountId(admin.getId())
                                .token(newRefreshToken)
                                .expiresAt(jwtProvider.getRefreshTokenExpiry())
                                .build())
                );

        return new LoginResponse(accessToken, newRefreshToken);
    }

    @Transactional(readOnly = true)
    public TokenRefreshResponse adminRefresh(String refreshToken) {
        jwtProvider.validateToken(refreshToken);

        RefreshToken saved = refreshTokenRepository
                .findByAccountTypeAndToken(AccountType.ADMIN, refreshToken)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        Admin admin = adminRepository.findById(saved.getAccountId())
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        return new TokenRefreshResponse(jwtProvider.generateAdminAccessToken(
                admin.getId(), admin.getUsername(), admin.getRole().name()));
    }

    @Transactional
    public void adminLogout(Long adminId) {
        refreshTokenRepository.deleteByAccountTypeAndAccountId(AccountType.ADMIN, adminId);
    }

    @Transactional(readOnly = true)
    public TokenRefreshResponse userRefresh(String cookieValue) {
        if (!StringUtils.hasText(cookieValue)) {
            throw new CustomException(ErrorCode.TOKEN_NOT_FOUND);
        }

        jwtProvider.validateToken(cookieValue);

        Long userId = Long.parseLong(jwtProvider.parseClaims(cookieValue).getSubject());

        RefreshToken saved = refreshTokenRepository
                .findByAccountTypeAndAccountId(AccountType.USER, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        if (!saved.getToken().equals(cookieValue)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        return new TokenRefreshResponse(jwtProvider.generateUserAccessToken(userId));
    }

    @Transactional
    public void userLogout(Long userId) {
        refreshTokenRepository.deleteByAccountTypeAndAccountId(AccountType.USER, userId);
    }
}
