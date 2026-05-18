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


@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminRepository adminRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(LoginRequest request) {

        Admin admin = adminRepository.findByUsernameAndDeletedAtIsNull(request.getUsername())
            .filter(a -> passwordEncoder.matches(request.getPassword(), a.getPassword()))
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        String accessToken = jwtProvider.generateAdminAccessToken(
            admin.getId(),
            admin.getUsername(),
            admin.getRole().name()
        );

        String newRefreshToken = jwtProvider.generateAdminRefreshToken(admin.getId());

        RefreshToken savedToken = refreshTokenRepository
            .findByAccountTypeAndAccountId(AccountType.ADMIN, admin.getId())
            .orElse(null);

        if (savedToken == null) {
            savedToken = RefreshToken.builder()
                .accountType(AccountType.ADMIN)
                .accountId(admin.getId())
                .token(newRefreshToken)
                .expiresAt(jwtProvider.getRefreshTokenExpiry())
                .build();
        } else {
            savedToken.update(newRefreshToken, jwtProvider.getRefreshTokenExpiry());
        }

        refreshTokenRepository.save(savedToken);

        return new LoginResponse(accessToken, newRefreshToken);
    }

    @Transactional(readOnly = true)
    public TokenRefreshResponse refresh(String refreshToken) {

        jwtProvider.validateToken(refreshToken);

        RefreshToken savedToken = refreshTokenRepository
            .findByAccountTypeAndToken(AccountType.ADMIN, refreshToken)
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        Admin admin = adminRepository.findById(savedToken.getAccountId())
            .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        String newAccessToken = jwtProvider.generateAdminAccessToken(
            admin.getId(),
            admin.getUsername(),
            admin.getRole().name()
        );

        return new TokenRefreshResponse(newAccessToken);
    }

    @Transactional
    public void logout(Long adminId) {
        refreshTokenRepository.deleteByAccountTypeAndAccountId(AccountType.ADMIN, adminId);
    }
}
