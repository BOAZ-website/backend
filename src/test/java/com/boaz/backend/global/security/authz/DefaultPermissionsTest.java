package com.boaz.backend.global.security.authz;

import com.boaz.backend.domain.admin.entity.Admin.Role;
import com.boaz.backend.domain.admin.entity.Admin.TeamName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static com.boaz.backend.global.security.authz.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기본 세트가 확정 권한 매트릭스(43행 × 8열, 2026-09-10 개정판)와 맞는지 본다.
 */
class DefaultPermissionsTest {

    @Nested
    @DisplayName("명시 키 6개")
    class ExactKeys {

        @Test
        @DisplayName("(MASTER, 서비스운영팀) 서운팀장 — 평가 4행만 X, 나머지 34개 전부")
        void master() {
            Set<Permission> actual = DefaultPermissions.of(Role.MASTER, TeamName.서비스운영팀);

            assertThat(actual).containsExactlyInAnyOrder(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ,
                    ADMIN_ACCOUNT_SELF_WRITE, ADMIN_ACCOUNT_WRITE,
                    ADMIN_ACCOUNT_CREATE_DELETE, ADMIN_PERMISSION_WRITE, AUDIT_LOG_READ,
                    CONTENT_READ, CONTENT_WRITE,
                    RECRUITMENT_NOTICE_READ, RECRUITMENT_NOTICE_WRITE, APPLICANT_CSV_READ,
                    APPLICATION_DELETE_ALL, PRE_NOTIFICATION_READ, PRE_NOTIFICATION_DELETE,
                    MEMBER_PROMOTION_WRITE,
                    ATTENDANCE_BASE_READ, ATTENDANCE_BASE_WRITE,
                    ATTENDANCE_ADV_READ, ATTENDANCE_ADV_WRITE,
                    ATTENDANCE_STUDY_READ, ATTENDANCE_STUDY_WRITE,
                    ATTENDANCE_EVENT_READ, ATTENDANCE_EVENT_WRITE,
                    ATTENDANCE_SCORE_READ, ATTENDANCE_SCORE_WRITE,
                    ATTENDANCE_ADV_ENTRY_READ, ATTENDANCE_ADV_ENTRY_WRITE,
                    ATTENDANCE_STUDY_ENTRY_READ, ATTENDANCE_STUDY_ENTRY_WRITE,
                    HOST_ACCOUNT_READ, HOST_ACCOUNT_WRITE,
                    SCORE_RULE_READ, SCORE_RULE_WRITE);
        }

        @Test
        @DisplayName("MASTER 는 최고 권한이 아니다 — 평가·최종합불 4개는 갖지 않는다")
        void masterHasNoEvaluation() {
            assertThat(DefaultPermissions.of(Role.MASTER, TeamName.서비스운영팀))
                    .doesNotContain(EVALUATION_OWN_TRACK_WRITE, EVALUATION_ALL_TRACK_WRITE,
                            FINAL_DECISION_READ, FINAL_DECISION_WRITE);
        }

        @Test
        @DisplayName("(SUPER, 대표진) 대표진")
        void representative() {
            Set<Permission> actual = DefaultPermissions.of(Role.SUPER, TeamName.대표진);

            assertThat(actual).containsExactlyInAnyOrder(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    CONTENT_READ,
                    RECRUITMENT_NOTICE_READ, PRE_NOTIFICATION_READ,
                    ATTENDANCE_BASE_READ, ATTENDANCE_ADV_READ, ATTENDANCE_STUDY_READ,
                    ATTENDANCE_EVENT_READ, ATTENDANCE_SCORE_READ,
                    ATTENDANCE_ADV_ENTRY_READ, ATTENDANCE_STUDY_ENTRY_READ,
                    HOST_ACCOUNT_READ,
                    SCORE_RULE_READ, SCORE_RULE_WRITE,
                    EVALUATION_OWN_TRACK_WRITE,
                    FINAL_DECISION_READ, FINAL_DECISION_WRITE);
        }

        @Test
        @DisplayName("(SUPER, 차기대표진) — 대표진과 `다른 부문 서류 평가` 1행만 다르다")
        void nextRepresentative() {
            Set<Permission> rep = DefaultPermissions.of(Role.SUPER, TeamName.대표진);
            Set<Permission> next = DefaultPermissions.of(Role.SUPER, TeamName.차기대표진);

            assertThat(next).contains(EVALUATION_ALL_TRACK_WRITE);
            assertThat(next).containsAll(rep);
            assertThat(diff(next, rep)).containsExactly(EVALUATION_ALL_TRACK_WRITE);
            assertThat(diff(rep, next)).isEmpty();
        }

        @Test
        @DisplayName("(TEAM, 서비스운영팀) — 계정 조회·감사로그·콘텐츠·리크루팅. 출결은 전부 X")
        void serviceOperationTeam() {
            Set<Permission> actual = DefaultPermissions.of(Role.TEAM, TeamName.서비스운영팀);

            assertThat(actual).containsExactlyInAnyOrder(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    AUDIT_LOG_READ,
                    CONTENT_READ, CONTENT_WRITE,
                    RECRUITMENT_NOTICE_READ, RECRUITMENT_NOTICE_WRITE, APPLICANT_CSV_READ,
                    APPLICATION_DELETE_ALL, PRE_NOTIFICATION_READ, PRE_NOTIFICATION_DELETE,
                    MEMBER_PROMOTION_WRITE,
                    EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ);

            assertThat(actual).noneMatch(p -> p.name().startsWith("ATTENDANCE_"));
        }

        @Test
        @DisplayName("(TEAM, 운영지원팀) — 출결 전반·점수 규칙·HOST 계정 발급. 계정 목록 조회는 X")
        void operationSupportTeam() {
            Set<Permission> actual = DefaultPermissions.of(Role.TEAM, TeamName.운영지원팀);

            assertThat(actual).containsExactlyInAnyOrder(
                    ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    ATTENDANCE_BASE_READ, ATTENDANCE_BASE_WRITE,
                    ATTENDANCE_ADV_READ, ATTENDANCE_ADV_WRITE,
                    ATTENDANCE_STUDY_READ, ATTENDANCE_STUDY_WRITE,
                    ATTENDANCE_EVENT_READ, ATTENDANCE_EVENT_WRITE,
                    ATTENDANCE_SCORE_READ, ATTENDANCE_SCORE_WRITE,
                    ATTENDANCE_ADV_ENTRY_READ, ATTENDANCE_ADV_ENTRY_WRITE,
                    ATTENDANCE_STUDY_ENTRY_READ, ATTENDANCE_STUDY_ENTRY_WRITE,
                    HOST_ACCOUNT_READ, HOST_ACCOUNT_WRITE,
                    SCORE_RULE_READ, SCORE_RULE_WRITE,
                    EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ);

            assertThat(actual).doesNotContain(ADMIN_ACCOUNT_READ);
        }

        @Test
        @DisplayName("(HOST, 그룹리더) — 스터디장·ADV팀장 두 열이 합쳐진 키. 본인 계정 조회·수정 둘뿐이고 빈 집합이 아니다")
        void groupLeader() {
            Set<Permission> actual = DefaultPermissions.of(Role.HOST, TeamName.그룹리더);

            assertThat(actual).containsExactlyInAnyOrder(
                    ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE);
        }
    }

    @Nested
    @DisplayName("TEAM 폴백 — 기타 운영진")
    class Fallback {

        @ParameterizedTest(name = "(TEAM, {0}) → 기타 운영진 4개")
        @EnumSource(value = TeamName.class, names = {"디자인팀", "자료연구팀", "기획팀", "대외협력팀"})
        void otherTeams(TeamName teamName) {
            assertThat(DefaultPermissions.of(Role.TEAM, teamName)).containsExactlyInAnyOrder(
                    ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ);
        }

        @Test
        @DisplayName("명시 키가 폴백보다 먼저다 — (TEAM, 운영지원팀)이 폴백으로 떨어지지 않는다")
        void exactBeatsFallback() {
            assertThat(DefaultPermissions.of(Role.TEAM, TeamName.운영지원팀))
                    .isNotEqualTo(DefaultPermissions.of(Role.TEAM, TeamName.기획팀));
        }
    }

    @Nested
    @DisplayName("없는 조합은 빈 집합 — 닫히는 쪽으로 실패한다")
    class NoKey {

        @Test
        @DisplayName("(SUPER, 그룹리더) 처럼 의미 없는 조합은 권한 0")
        void superGroupLeader() {
            assertThat(DefaultPermissions.of(Role.SUPER, TeamName.그룹리더)).isEmpty();
        }

        @Test
        @DisplayName("HOST 에는 폴백이 없다 — (HOST, 기획팀)은 권한 0")
        void hostWithoutGroupLeaderTeam() {
            assertThat(DefaultPermissions.of(Role.HOST, TeamName.기획팀)).isEmpty();
        }

        @Test
        @DisplayName("MASTER 에도 폴백이 없다 — (MASTER, 기획팀)은 권한 0")
        void masterWithOtherTeam() {
            assertThat(DefaultPermissions.of(Role.MASTER, TeamName.기획팀)).isEmpty();
        }
    }

    private static Set<Permission> diff(Set<Permission> a, Set<Permission> b) {
        return a.stream().filter(p -> !b.contains(p)).collect(java.util.stream.Collectors.toSet());
    }
}
