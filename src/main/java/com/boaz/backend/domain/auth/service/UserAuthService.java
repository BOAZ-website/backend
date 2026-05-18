package com.boaz.backend.domain.auth.service;

import com.boaz.backend.domain.auth.dto.response.UserTokenRefreshResponse;
import com.boaz.backend.domain.auth.entity.RefreshToken;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.global.common.enums.AccountType;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    // AUTH-005 User 토큰 재발급
    @Transactional(readOnly = true)
    public UserTokenRefreshResponse refreshToken(String cookieValue) {
        if (!StringUtils.hasText(cookieValue)) {
            throw new CustomException(ErrorCode.TOKEN_NOT_FOUND);
        }

        // JWT 서명 + 만료 검증
        jwtProvider.validateToken(cookieValue);

        Long userId = Long.parseLong(jwtProvider.parseClaims(cookieValue).getSubject());

        // DB에서 해당 유저의 refresh token 조회 및 일치 여부 확인
        RefreshToken saved = refreshTokenRepository
                .findByAccountTypeAndAccountId(AccountType.USER, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        if (!saved.getToken().equals(cookieValue)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        return new UserTokenRefreshResponse(jwtProvider.generateUserAccessToken(userId));
    }

    // AUTH-006 User 로그아웃
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, userId)
                .ifPresent(refreshTokenRepository::delete);
    }
}
