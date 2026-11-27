package com.boaz.backend.global.security.authz;

import com.boaz.backend.domain.admin.entity.Admin.Role;
import com.boaz.backend.domain.admin.entity.Admin.TeamName;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.boaz.backend.global.security.authz.Permission.*;

/**
 * 역할과 팀 조합별 기본 권한. 권한 매트릭스 표를 옮긴 것이다.
 * <p>표에 있는 조합은 {@code EXACT}에서, 기타 운영진 팀은 {@code FALLBACK}에서 찾는다. 표에 없는 조합은 권한이 없다.
 */
public final class DefaultPermissions {

    private DefaultPermissions() {
    }

    private record Key(Role role, TeamName team) {
    }

    private static Key key(Role role, TeamName team) {
        return new Key(role, team);
    }

    private static Set<Permission> set(Permission... permissions) {
        return Collections.unmodifiableSet(EnumSet.copyOf(Set.of(permissions)));
    }

    private static final Map<Key, Set<Permission>> EXACT = Map.of(

            // 서운팀장 (MASTER)
            key(Role.MASTER, TeamName.서비스운영팀), set(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ,
                    ADMIN_ACCOUNT_SELF_WRITE, ADMIN_ACCOUNT_WRITE,
                    ADMIN_ACCOUNT_CREATE_DELETE, ADMIN_PERMISSION_WRITE, AUDIT_LOG_READ,
                    CONTENT_WRITE,
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
                    SCORE_RULE_READ, SCORE_RULE_WRITE,
                    EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ),

            // 대표진 (SUPER)
            key(Role.SUPER, TeamName.대표진), set(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    RECRUITMENT_NOTICE_READ, PRE_NOTIFICATION_READ,
                    ATTENDANCE_BASE_READ, ATTENDANCE_ADV_READ, ATTENDANCE_STUDY_READ,
                    ATTENDANCE_EVENT_READ, ATTENDANCE_SCORE_READ,
                    ATTENDANCE_ADV_ENTRY_READ, ATTENDANCE_STUDY_ENTRY_READ,
                    HOST_ACCOUNT_READ,
                    SCORE_RULE_READ, SCORE_RULE_WRITE,
                    EVALUATION_OWN_TRACK_WRITE,
                    FINAL_DECISION_READ, FINAL_DECISION_WRITE),

            // 차기대표진 (SUPER)
            key(Role.SUPER, TeamName.차기대표진), set(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    RECRUITMENT_NOTICE_READ, PRE_NOTIFICATION_READ,
                    ATTENDANCE_BASE_READ, ATTENDANCE_ADV_READ, ATTENDANCE_STUDY_READ,
                    ATTENDANCE_EVENT_READ, ATTENDANCE_SCORE_READ,
                    ATTENDANCE_ADV_ENTRY_READ, ATTENDANCE_STUDY_ENTRY_READ,
                    HOST_ACCOUNT_READ,
                    SCORE_RULE_READ, SCORE_RULE_WRITE,
                    EVALUATION_OWN_TRACK_WRITE, EVALUATION_ALL_TRACK_WRITE,
                    FINAL_DECISION_READ, FINAL_DECISION_WRITE),

            // 서비스운영팀 (TEAM)
            key(Role.TEAM, TeamName.서비스운영팀), set(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    AUDIT_LOG_READ,
                    CONTENT_WRITE,
                    RECRUITMENT_NOTICE_READ, RECRUITMENT_NOTICE_WRITE, APPLICANT_CSV_READ,
                    APPLICATION_DELETE_ALL, PRE_NOTIFICATION_READ, PRE_NOTIFICATION_DELETE,
                    MEMBER_PROMOTION_WRITE,
                    EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ),

            // 운영지원팀 (TEAM)
            key(Role.TEAM, TeamName.운영지원팀), set(
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
                    EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ),

            // 스터디장, ADV 팀장 (HOST)
            key(Role.HOST, TeamName.그룹리더), set(
                    ADMIN_ACCOUNT_SELF_WRITE)
    );

    /** 기타 운영진 기본 권한. */
    private static final Map<Role, Set<Permission>> FALLBACK = Map.of(
            Role.TEAM, set(ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                           EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ)
    );

    /** 기타 운영진에 속하는 팀. 이 밖의 TEAM 계정은 폴백을 받지 않는다. */
    private static final Set<TeamName> TEAM_FALLBACK_TEAMS = Set.of(
            TeamName.디자인팀,
            TeamName.자료연구팀,
            TeamName.기획팀,
            TeamName.대외협력팀
    );

    /** 표에 있는 조합인지 확인한다. 계정을 저장할 때 쓴다. */
    public static boolean isValidCombination(Role role, TeamName team) {
        return EXACT.containsKey(key(role, team))
                || (role == Role.TEAM && TEAM_FALLBACK_TEAMS.contains(team));
    }

    /** 기본 권한을 돌려준다. 표에 없는 조합이면 빈 집합이다. */
    public static Set<Permission> of(Role role, TeamName team) {
        Set<Permission> exact = EXACT.get(key(role, team));
        if (exact != null) {
            return exact;
        }
        if (role == Role.TEAM && TEAM_FALLBACK_TEAMS.contains(team)) {
            return FALLBACK.get(Role.TEAM);
        }
        return Set.of();
    }
}
