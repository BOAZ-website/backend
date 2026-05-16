package com.boaz.backend.domain.auth.service;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.auth.dto.request.LoginRequest;
import com.boaz.backend.domain.auth.dto.response.LoginResponse;
import com.boaz.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.boaz.backend.domain.auth.entity.RefreshToken;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
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

    // AUTH-001 로그인
    @Transactional
    public LoginResponse login(LoginRequest request) {

        // 아이디 & 비밀번호 검증 (계정 존재 여부 노출 방지를 위해 동일 에러 반환)
        Admin admin = adminRepository.findByUsernameAndDeletedAtIsNull(request.getUsername())
            .filter(a -> passwordEncoder.matches(request.getPassword(), a.getPassword()))
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));  // 아이디 또는 비밀번호 불일치한 경우 

        // 검증 통과 시 Access Token 발급 
        String accessToken = jwtProvider.generateAccessToken(
            admin.getId(), 
            admin.getUsername(), 
            admin.getRole().name()
        );

        // Refresh Token 발급 
        String newRefreshToken = jwtProvider.generateRefreshToken(admin.getId());

        // 해당 계정의 기존 RefreshToken이 DB에 있는지 조회
        RefreshToken savedToken = refreshTokenRepository.findByAdminId(admin.getId()).orElse(null);
        
        // 기존 토큰 없으면 새로 생성, 있으면 덮어쓰기 (단일 로그인 정책)
        if (savedToken == null) {

            // 최초 로그인 (새 row 생성)
            savedToken = RefreshToken.builder()
                .adminId(admin.getId())
                .token(newRefreshToken)
                .expiresAt(jwtProvider.getRefreshTokenExpiry())
                .build();
        } else {
            // 재로그인 (기존 row의 token, expiresAt 값만 교체)
            savedToken.rotate(newRefreshToken, jwtProvider.getRefreshTokenExpiry());
        }

        // DB에 저장
        refreshTokenRepository.save(savedToken);

        // accessToken은 Body로, refreshToken은 쿠키로 전달하기 위해 같이 반환 
        return new LoginResponse(accessToken, newRefreshToken);
    }

    // AUTH-002 토큰 갱신
    @Transactional(readOnly = true)
    public TokenRefreshResponse refresh(String refreshToken) {

        // JWT 서명 및 만료 검증
        jwtProvider.validateToken(refreshToken);

        // 전달 받은 토큰이 DB에 저장된 값과 일치하는지 확인 
        RefreshToken savedToken = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));  // DB 불일치 시 예외처리

        // AdminId로 관리자 조회 (Access Token 발급 시 유저 정보 필요)
        Admin admin = adminRepository.findById(savedToken.getAdminId())
            .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
        
        // 검증 통과 시, 새 Access Token 발급 
        String newAccessToken = jwtProvider.generateAccessToken(
            admin.getId(), 
            admin.getUsername(), 
            admin.getRole().name()
        );

        return new TokenRefreshResponse(newAccessToken);
    }

    // AUTH-003 로그아웃
    @Transactional
    public void logout(Long adminId) {

        // RefreshToken 삭제 
        refreshTokenRepository.deleteByAdminId(adminId);

    }
}
