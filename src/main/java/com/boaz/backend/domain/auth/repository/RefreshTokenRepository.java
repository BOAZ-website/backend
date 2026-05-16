package com.boaz.backend.domain.auth.repository;

import com.boaz.backend.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // token 값으로 RefreshToken 조회 (DB 일치 여부 검증)
    Optional<RefreshToken> findByToken(String token);

    // adminId로 RefreshToken 조회 (기존 토큰 존재 여부 확인)
    Optional<RefreshToken> findByAdminId(Long adminId);

    // adminId로 RefreshToken 삭제 (토큰 무효화)
    void deleteByAdminId(Long adminId);
}
