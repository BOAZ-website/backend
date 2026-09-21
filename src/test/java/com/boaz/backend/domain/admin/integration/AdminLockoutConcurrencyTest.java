package com.boaz.backend.domain.admin.integration;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.domain.admin.repository.AdminPermissionOverrideRepository;
import com.boaz.backend.domain.admin.repository.AdminRepository;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.domain.admin.service.AdminService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.security.authz.EffectivePermissions;
import com.boaz.backend.global.security.authz.Permission;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 락아웃 가드가 <b>동시 요청 두 건</b>에도 성립하는지 본다.
 *
 * <p>가드가 지키는 규칙("{@code ADMIN_ACCOUNT_CREATE_DELETE} 보유자가 최소 한 명")은 행 하나가 아니라
 * <b>집합</b>에 걸린 조건이라, 대상 행만 보는 판정으로는 지켜지지 않는다. 보유자 둘이 서로를 지우면
 * 각자 "나 말고 한 명 더 있다"를 읽고 <b>둘 다 통과</b>해 보유자가 0이 된다 — 쓰기 스큐다.
 * MySQL 의 기본 격리 수준(REPEATABLE READ)은 이걸 막지 않는다.
 *
 * <p>그래서 {@code AdminService} 의 축소 경로는 트랜잭션의 <b>첫 DB 접근</b>으로
 * {@code AdminRepository.findAllLiveForUpdate()}(= {@code SELECT ... FOR UPDATE})를 불러
 * <b>세는 집합 전체</b>를 잠근다. 이 테스트는 그 잠금이 실제로 두 번째 요청을 세워 두는지 확인한다.
 *
 * <p><b>타이밍에 기대지 않는다.</b> 첫 번째 요청이 커밋 전에 멈춰 있는 동안 두 번째 요청을 들여보내므로,
 * 잠금이 없으면 두 번째가 낡은 스냅샷(= 아직 살아 있는 첫 번째 계정)을 읽고 반드시 통과한다.
 * ⇒ 잠금을 빼면 이 테스트는 확률이 아니라 <b>항상</b> 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ADMIN-005 deleteAccount 동시성 — 락아웃 가드")
class AdminLockoutConcurrencyTest extends TestcontainersBase {

    /** 첫 번째 트랜잭션이 커밋을 미루고 붙잡고 있는 시간. 두 번째가 판정까지 들어올 여유면 된다. */
    private static final long HOLD_MILLIS = 800L;

    @Autowired AdminService adminService;
    @Autowired AdminRepository adminRepository;
    @Autowired AdminPermissionOverrideRepository overrideRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EffectivePermissions effectivePermissions;
    @Autowired PlatformTransactionManager txManager;

    private TransactionTemplate tx;
    private Long firstId;
    private Long secondId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        clearAdmins();
        // 이 테스트는 커밋을 실제로 내므로 @Transactional 롤백에 기댈 수 없다.
        // 보유자가 정확히 둘이어야 전제가 성립하므로 테이블을 비우고 시작한다.
        firstId = saveMaster().getId();
        secondId = saveMaster().getId();
        assertThat(remainingManagers()).isEqualTo(2);
    }

    @AfterEach
    void tearDown() {
        clearAdmins();
    }

    @Test
    @DisplayName("TC-006 두 보유자가 동시에 서로를 지우면 한쪽만 성공한다 — 남는 보유자 1명")
    void concurrentMutualDeleteKeepsOneManager() throws Exception {
        CountDownLatch firstDeletedNotCommitted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> deletesFirst = pool.submit(() -> tx.execute(status -> {
                adminService.deleteAccount(firstId);   // 바깥 트랜잭션에 합류한다 — 아직 커밋 전이다
                firstDeletedNotCommitted.countDown();
                // 두 번째 요청이 판정까지 들어올 시간을 준다.
                // 잠금이 있으면 그쪽은 여기서 멈춰 이 트랜잭션의 커밋을 기다린다.
                sleepQuietly(HOLD_MILLIS);
                return null;
            }));

            Future<ErrorCode> deletesSecond = pool.submit(() -> {
                firstDeletedNotCommitted.await();
                try {
                    adminService.deleteAccount(secondId);
                    return null;                        // 성공 = 가드를 통과했다
                } catch (CustomException e) {
                    return e.getErrorCode();
                }
            });

            deletesFirst.get(10, TimeUnit.SECONDS);
            assertThat(deletesSecond.get(10, TimeUnit.SECONDS))
                    .as("두 번째 요청은 마지막 보유자를 지우려 한 것이므로 막혀야 한다")
                    .isEqualTo(ErrorCode.LAST_ACCOUNT_MANAGER);
        } finally {
            pool.shutdownNow();
        }

        assertThat(adminRepository.findByIdAndDeletedAtIsNull(firstId)).isEmpty();
        assertThat(adminRepository.findByIdAndDeletedAtIsNull(secondId)).isPresent();
        assertThat(remainingManagers())
                .as("보유자가 0이 되면 계정 관리 기능이 복구 불가로 끊긴다")
                .isEqualTo(1);
    }

    // ──────────────────────────────────────────────

    private long remainingManagers() {
        List<Admin> live = adminRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc();
        return effectivePermissions.of(live).values().stream()
                .filter(p -> p.contains(Permission.ADMIN_ACCOUNT_CREATE_DELETE))
                .count();
    }

    /** 계정 CRUD 권한을 다 가진 유일한 키. */
    private Admin saveMaster() {
        return adminRepository.save(Admin.builder()
                .username("mgr-" + System.nanoTime())
                .password(passwordEncoder.encode("Boaz1234!"))
                .role(Admin.Role.MASTER)
                .name("계정관리자")
                .track(Track.ANALYSIS)
                .term(25)
                .teamName(Admin.TeamName.서비스운영팀)
                .createdBy(null)
                .build());
    }

    private void clearAdmins() {
        refreshTokenRepository.deleteAll();
        overrideRepository.deleteAll();
        adminRepository.deleteAll();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
