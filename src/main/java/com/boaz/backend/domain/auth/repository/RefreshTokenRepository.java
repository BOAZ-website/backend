package com.boaz.backend.domain.auth.repository;

import com.boaz.backend.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByAdminId(Long adminId);

    void deleteByAdminId(Long adminId);
}
