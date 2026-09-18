package com.boaz.backend.global.security.authz;

/**
 * 관리자 기능 단위 권한. 이름 규칙은 {@code <대상>_<동작>}이고 동작은 READ / WRITE 둘.
 * 설계 문서 카탈로그와 1:1이다.
 */
public enum Permission {

    // ── 계정·시스템 ────────────────────────────────────────────────
    ADMIN_ACCOUNT_READ,
    /** 본인 계정 조회. 대상이 본인인지는 {@link ScopeGuard} 축 ③이 본다. */
    ADMIN_ACCOUNT_SELF_READ,
    /** 본인 계정 정보 수정 / 비밀번호 변경. */
    ADMIN_ACCOUNT_SELF_WRITE,
    /** 타 계정 정보 수정 / 비밀번호 변경. */
    ADMIN_ACCOUNT_WRITE,
    ADMIN_ACCOUNT_CREATE_DELETE,
    /** 권한 커스텀 API에 붙일 권한. */
    ADMIN_PERMISSION_WRITE,
    /** 감사 로그 읽기 권한. */
    AUDIT_LOG_READ,

    // ── 콘텐츠 (archive · curriculum · faq · review 네 도메인에 걸친다) ──
    CONTENT_READ,
    CONTENT_WRITE,

    // ── 리크루팅 ──────────────────────────────────────────────────
    RECRUITMENT_NOTICE_READ,
    RECRUITMENT_NOTICE_WRITE,
    APPLICANT_CSV_READ,
    APPLICATION_DELETE_ALL,
    PRE_NOTIFICATION_READ,
    PRE_NOTIFICATION_DELETE,
    MEMBER_PROMOTION_WRITE,

    // 평가 최종합불
    /** 본인 부문 서류 평가. 범위(부문)는 이름이 아니라 {@link ScopeGuard} 축 ①이 본다. */
    EVALUATION_OWN_TRACK_WRITE,
    EVALUATION_ALL_TRACK_WRITE,
    FINAL_DECISION_READ,
    FINAL_DECISION_WRITE,

    // 출결 관리 ──────────────────────────────────────────────────
    ATTENDANCE_BASE_READ,
    ATTENDANCE_BASE_WRITE,
    ATTENDANCE_ADV_READ,
    ATTENDANCE_ADV_WRITE,
    ATTENDANCE_STUDY_READ,
    ATTENDANCE_STUDY_WRITE,
    ATTENDANCE_EVENT_READ,
    ATTENDANCE_EVENT_WRITE,
    ATTENDANCE_SCORE_READ,
    ATTENDANCE_SCORE_WRITE,

    // 출결 입력 ──────────────────────────────────────────────────
    // C·U(주차 안지남)·U(주차 지남)·D 4행이 permission 2개로 접힌다. 주차·삭제 제약은
    // 이름이 아니라 2층 표기와 ScopeGuard 에 있다.
    ATTENDANCE_ADV_ENTRY_READ,
    ATTENDANCE_ADV_ENTRY_WRITE,
    ATTENDANCE_STUDY_ENTRY_READ,
    ATTENDANCE_STUDY_ENTRY_WRITE,

    // 출결 입력 (담당 그룹 한정) ──────────────────────────────────────────────────
    // 기본 세트에 없다. admin_group_leader ⋈ activity_group 에서 매 요청 파생된다
    ATTENDANCE_ADV_ENTRY_OWN_GROUP_READ,
    ATTENDANCE_ADV_ENTRY_OWN_GROUP_WRITE,
    ATTENDANCE_STUDY_ENTRY_OWN_GROUP_READ,
    ATTENDANCE_STUDY_ENTRY_OWN_GROUP_WRITE,

    // ── 기타 ──────────────────────────────────────────────────
    HOST_ACCOUNT_READ,
    HOST_ACCOUNT_WRITE,
    SCORE_RULE_READ,
    SCORE_RULE_WRITE
}
