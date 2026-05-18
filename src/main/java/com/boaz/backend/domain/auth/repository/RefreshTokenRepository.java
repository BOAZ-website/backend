package com.boaz.backend.domain.auth.repository;

import com.boaz.backend.domain.auth.entity.RefreshToken;
import com.boaz.backend.global.common.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByAccountTypeAndAccountId(AccountType accountType, Long accountId);

    Optional<RefreshToken> findByAccountTypeAndToken(AccountType accountType, String token);

    void deleteByAccountTypeAndAccountId(AccountType accountType, Long accountId);
}
