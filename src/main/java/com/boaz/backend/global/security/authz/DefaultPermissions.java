package com.boaz.backend.global.security.authz;

import com.boaz.backend.domain.admin.entity.Admin.Role;
import com.boaz.backend.domain.admin.entity.Admin.TeamName;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.boaz.backend.global.security.authz.Permission.*;

/**
 * {@code (role, teamName)} → 기본 권한 세트. 확정 권한 매트릭스(2026.09.11 기준 43행 × 8열)를 코드로 옮긴 것이다.
 * <p>매트릭스 열은 8개지만 키는 <b>명시 6 + {@code TEAM} 폴백 1 = 7개</b>다. {@code 스터디장}·{@code ADV팀장}
 * 두 열은 {@code (HOST, 그룹리더)} 한 키로 합쳐진다.
 *
 * <p>조회 순서는 <b>명시 키 → role 폴백 → 빈 집합</b>이다. 폴백은 {@code TEAM}에만 둔다 —
 * {@code (SUPER, 그룹리더)} 같은 무의미한 조합은 권한 0으로 <b>닫히는 쪽</b>으로 실패해야 한다.
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

    /** 명시 키 6개. 각 집합은 매트릭스 열을 그대로 옮긴 것이다. */
    private static final Map<Key, Set<Permission>> EXACT = Map.of(

            // 서운팀장 — 서류 평가 4행만 X. 평가 결정에 대한 권한은 없다.
            key(Role.MASTER, TeamName.서비스운영팀), set(
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
                    SCORE_RULE_READ, SCORE_RULE_WRITE),

            // 대표진 — 차기대표진과 `다른 부문 서류 평가` 1행만 다르다.
            key(Role.SUPER, TeamName.대표진), set(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    CONTENT_READ,
                    RECRUITMENT_NOTICE_READ, PRE_NOTIFICATION_READ,
                    ATTENDANCE_BASE_READ, ATTENDANCE_ADV_READ, ATTENDANCE_STUDY_READ,
                    ATTENDANCE_EVENT_READ, ATTENDANCE_SCORE_READ,
                    ATTENDANCE_ADV_ENTRY_READ, ATTENDANCE_STUDY_ENTRY_READ,
                    HOST_ACCOUNT_READ,
                    SCORE_RULE_READ, SCORE_RULE_WRITE,
                    EVALUATION_OWN_TRACK_WRITE,
                    FINAL_DECISION_READ, FINAL_DECISION_WRITE),

            // 차기대표진 — 대표진 + 다른 부문 서류 평가.
            key(Role.SUPER, TeamName.차기대표진), set(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    CONTENT_READ,
                    RECRUITMENT_NOTICE_READ, PRE_NOTIFICATION_READ,
                    ATTENDANCE_BASE_READ, ATTENDANCE_ADV_READ, ATTENDANCE_STUDY_READ,
                    ATTENDANCE_EVENT_READ, ATTENDANCE_SCORE_READ,
                    ATTENDANCE_ADV_ENTRY_READ, ATTENDANCE_STUDY_ENTRY_READ,
                    HOST_ACCOUNT_READ,
                    SCORE_RULE_READ, SCORE_RULE_WRITE,
                    EVALUATION_OWN_TRACK_WRITE, EVALUATION_ALL_TRACK_WRITE,
                    FINAL_DECISION_READ, FINAL_DECISION_WRITE),

            // 서비스운영팀 — 계정 조회·감사로그·콘텐츠·리크루팅. 출결은 전부 X.
            key(Role.TEAM, TeamName.서비스운영팀), set(
                    ADMIN_ACCOUNT_READ, ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                    AUDIT_LOG_READ,
                    CONTENT_READ, CONTENT_WRITE,
                    RECRUITMENT_NOTICE_READ, RECRUITMENT_NOTICE_WRITE, APPLICANT_CSV_READ,
                    APPLICATION_DELETE_ALL, PRE_NOTIFICATION_READ, PRE_NOTIFICATION_DELETE,
                    MEMBER_PROMOTION_WRITE,
                    EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ),

            // 운영지원팀 — 출결 전반·점수 규칙·HOST 계정 발급. 계정 목록 조회는 X.
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

            // 스터디장 + ADV팀장 두 열이 여기 하나로 합쳐진다. 입력탭 _OWN_GROUP_ permission은
            // 여기 없다 — admin_group_leader ⋈ activity_group 에서 계산된다.
            key(Role.HOST, TeamName.그룹리더), set(
                    ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE)
    );

    /**
     * role 단위 폴백 — 매트릭스의 "기타 운영진" 열. 디자인팀·자료연구팀·기획팀·대외협력팀 넷이 같은 값이라
     * 키로 다 적는 대신 폴백 하나로 둔다.
     */
    private static final Map<Role, Set<Permission>> FALLBACK = Map.of(
            Role.TEAM, set(ADMIN_ACCOUNT_SELF_READ, ADMIN_ACCOUNT_SELF_WRITE,
                           EVALUATION_OWN_TRACK_WRITE, FINAL_DECISION_READ)
    );

    /** 명시 키 → role 폴백 → 빈 집합(권한 0). 반환값은 불변이다. */
    public static Set<Permission> of(Role role, TeamName team) {
        Set<Permission> exact = EXACT.get(key(role, team));
        if (exact != null) {
            return exact;
        }
        return FALLBACK.getOrDefault(role, Set.of());
    }
}
