package com.boaz.backend.global.security.authz;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.support.AuthFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.boaz.backend.global.security.authz.Permission.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 3층 범위 판정. 2층을 이미 통과한 주체가 <b>대상</b>을 봐야만 갈리는 지점만 본다.
 *
 * <p>검증의 핵심은 <b>순서</b>다 — 넓은 permission을 먼저 보지 않으면 전체 권한자가 "본인 것 아님"으로
 * 거부된다. 각 메서드마다 그 역전이 일어나는 조합(전체 권한 + 남의 대상)을 따로 둔다.
 *
 * <p>권한은 이제 인자로 받는다. 즉 여기 넘기는 집합이 곧 {@code AdminUserDetails}가 들고 다니는 집합이고,
 * {@code EffectivePermissions}를 다시 부르지 않는다.
 */
class ScopeGuardTest {

    private final ScopeGuard scopeGuard = new ScopeGuard();

    private static final long SELF = 1L;
    private static final long OTHER = 2L;

    private Admin self() {
        return AuthFixtures.admin(SELF, Admin.Role.TEAM, Admin.TeamName.서비스운영팀);
    }

    private Admin self(Track track) {
        return AuthFixtures.admin(SELF, Admin.Role.TEAM, Admin.TeamName.서비스운영팀, track);
    }

    @Nested
    @DisplayName("축 ① 부문 — checkTrack")
    class CheckTrack {

        @Test
        @DisplayName("전 부문 권한자는 남의 부문도 통과한다")
        void allTrackPassesOtherTrack() {
            Admin admin = self(Track.ANALYSIS);

            assertThatCode(() -> scopeGuard.checkTrack(
                    admin, Set.of(EVALUATION_ALL_TRACK_WRITE), Track.ENGINEERING))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("전 부문 권한자는 본인 부문도 당연히 통과한다")
        void allTrackPassesOwnTrack() {
            Admin admin = self(Track.ANALYSIS);

            assertThatCode(() -> scopeGuard.checkTrack(
                    admin, Set.of(EVALUATION_ALL_TRACK_WRITE), Track.ANALYSIS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("본인 부문 권한자는 본인 부문만 통과한다")
        void ownTrackPassesOwnTrack() {
            Admin admin = self(Track.ANALYSIS);

            assertThatCode(() -> scopeGuard.checkTrack(
                    admin, Set.of(EVALUATION_OWN_TRACK_WRITE), Track.ANALYSIS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("본인 부문 권한자가 남의 부문을 평가하면 거부된다")
        void ownTrackDeniesOtherTrack() {
            Admin admin = self(Track.ANALYSIS);

            assertThatThrownBy(() -> scopeGuard.checkTrack(
                    admin, Set.of(EVALUATION_OWN_TRACK_WRITE), Track.ENGINEERING))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("권한 0이어도 본인 부문이면 3층은 통과한다 — 보유 여부는 2층 몫이다")
        void emptyPermissionsStillPassesOwnTrack() {
            Admin admin = self(Track.ANALYSIS);

            assertThatCode(() -> scopeGuard.checkTrack(admin, Set.of(), Track.ANALYSIS))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("축 ③ 본인 — checkAccountWrite")
    class CheckAccountWrite {

        @Test
        @DisplayName("ADMIN_ACCOUNT_WRITE 보유자는 남의 계정도 통과한다")
        void writePassesOther() {
            assertThatCode(() -> scopeGuard.checkAccountWrite(
                    self(), Set.of(ADMIN_ACCOUNT_WRITE), OTHER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SELF_WRITE 만 있으면 본인 계정은 통과한다")
        void selfWritePassesSelf() {
            assertThatCode(() -> scopeGuard.checkAccountWrite(
                    self(), Set.of(ADMIN_ACCOUNT_SELF_WRITE), SELF))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SELF_WRITE 만 있으면 남의 계정은 거부된다")
        void selfWriteDeniesOther() {
            assertThatThrownBy(() -> scopeGuard.checkAccountWrite(
                    self(), Set.of(ADMIN_ACCOUNT_SELF_WRITE), OTHER))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("둘 다 있으면 넓은 쪽이 먼저다 — 남의 계정에서 거부되지 않는다")
        void widerPermissionWinsOverSelfOnly() {
            assertThatCode(() -> scopeGuard.checkAccountWrite(
                    self(), Set.of(ADMIN_ACCOUNT_SELF_WRITE, ADMIN_ACCOUNT_WRITE), OTHER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SELF_READ 로는 쓰기 범위가 열리지 않는다 — 읽기 권한과 섞이지 않는다")
        void readPermissionDoesNotOpenWrite() {
            assertThatThrownBy(() -> scopeGuard.checkAccountWrite(
                    self(), Set.of(ADMIN_ACCOUNT_READ), OTHER))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("축 ③ 본인 — checkAccountRead")
    class CheckAccountRead {

        @Test
        @DisplayName("ADMIN_ACCOUNT_READ 보유자는 남의 계정도 통과한다")
        void readPassesOther() {
            assertThatCode(() -> scopeGuard.checkAccountRead(
                    self(), Set.of(ADMIN_ACCOUNT_READ), OTHER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SELF_READ 만 있으면 본인 계정은 통과한다")
        void selfReadPassesSelf() {
            assertThatCode(() -> scopeGuard.checkAccountRead(
                    self(), Set.of(ADMIN_ACCOUNT_SELF_READ), SELF))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SELF_READ 만 있으면 남의 계정은 거부된다")
        void selfReadDeniesOther() {
            assertThatThrownBy(() -> scopeGuard.checkAccountRead(
                    self(), Set.of(ADMIN_ACCOUNT_SELF_READ), OTHER))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("둘 다 있으면 넓은 쪽이 먼저다 — 남의 계정에서 거부되지 않는다")
        void widerPermissionWinsOverSelfOnly() {
            assertThatCode(() -> scopeGuard.checkAccountRead(
                    self(), Set.of(ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_READ), OTHER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ADMIN_ACCOUNT_WRITE 로는 조회 범위가 열리지 않는다")
        void writePermissionDoesNotOpenRead() {
            assertThatThrownBy(() -> scopeGuard.checkAccountRead(
                    self(), Set.of(ADMIN_ACCOUNT_WRITE), OTHER))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("기본 세트를 그대로 넣었을 때 — 매트릭스 열이 실제로 이렇게 갈린다")
    class WithDefaultSets {

        @Test
        @DisplayName("(MASTER, 서비스운영팀)은 ADMIN_ACCOUNT_WRITE 보유자라 남의 계정을 통과한다")
        void masterPassesOtherAccount() {
            Admin master = AuthFixtures.admin(SELF, Admin.Role.MASTER, Admin.TeamName.서비스운영팀);
            Set<Permission> permissions =
                    DefaultPermissions.of(Admin.Role.MASTER, Admin.TeamName.서비스운영팀);

            assertThatCode(() -> scopeGuard.checkAccountWrite(master, permissions, OTHER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("(TEAM, 운영지원팀)은 ADMIN_ACCOUNT_READ 가 없어 남의 계정 조회가 거부된다")
        void supportTeamDeniedOnOtherAccount() {
            Admin support = AuthFixtures.admin(SELF, Admin.Role.TEAM, Admin.TeamName.운영지원팀);
            Set<Permission> permissions =
                    DefaultPermissions.of(Admin.Role.TEAM, Admin.TeamName.운영지원팀);

            assertThatThrownBy(() -> scopeGuard.checkAccountRead(support, permissions, OTHER))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("(TEAM, 운영지원팀)도 본인 계정 조회는 통과한다 — SELF_READ 를 갖는다")
        void supportTeamPassesOnSelf() {
            Admin support = AuthFixtures.admin(SELF, Admin.Role.TEAM, Admin.TeamName.운영지원팀);
            Set<Permission> permissions =
                    DefaultPermissions.of(Admin.Role.TEAM, Admin.TeamName.운영지원팀);

            assertThatCode(() -> scopeGuard.checkAccountRead(support, permissions, SELF))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("(SUPER, 차기대표진)은 전 부문 평가 권한이라 남의 부문도 통과한다")
        void nextRepPassesOtherTrack() {
            Admin nextRep = AuthFixtures.admin(
                    SELF, Admin.Role.SUPER, Admin.TeamName.차기대표진, Track.ANALYSIS);
            Set<Permission> permissions =
                    DefaultPermissions.of(Admin.Role.SUPER, Admin.TeamName.차기대표진);

            assertThatCode(() -> scopeGuard.checkTrack(nextRep, permissions, Track.ENGINEERING))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("(SUPER, 대표진)은 본인 부문 평가뿐이라 남의 부문이 거부된다")
        void currentRepDeniedOnOtherTrack() {
            Admin rep = AuthFixtures.admin(
                    SELF, Admin.Role.SUPER, Admin.TeamName.대표진, Track.ANALYSIS);
            Set<Permission> permissions =
                    DefaultPermissions.of(Admin.Role.SUPER, Admin.TeamName.대표진);

            assertThatThrownBy(() -> scopeGuard.checkTrack(rep, permissions, Track.ENGINEERING))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }
}
