package com.boaz.backend.global.security.authz;

/**
 * 관리자 기능 단위 권한. 선언 순서는 권한 매트릭스 표의 행 순서를 따른다.
 * <p>한글 이름은 "기능 이름 (CRUD 약자)" 형식으로 쓴다.
 */
public enum Permission {

    // ── 계정과 시스템 ─────────────────────────────────────────────
    ADMIN_ACCOUNT_READ("운영진 계정 목록 조회 (R)"),
    ADMIN_ACCOUNT_SELF_READ("본인 계정 정보 조회 (R)"),
    ADMIN_ACCOUNT_SELF_WRITE("본인 계정 정보 수정 / 비밀번호 변경 (U)"),
    ADMIN_ACCOUNT_WRITE("타 계정 정보 수정 / 비밀번호 변경 (U)"),
    ADMIN_ACCOUNT_CREATE_DELETE("운영진 계정 생성 / 삭제 (CD)"),
    ADMIN_PERMISSION_WRITE("운영진 계정별 권한 부여 (CUD)"),
    AUDIT_LOG_READ("보안 감사 로그 조회 (R)"),

    // ── 콘텐츠 ────────────────────────────────────────────────────
    CONTENT_WRITE("콘텐츠(아카이빙, FAQ, 수료자 후기, 커리큘럼) 관리 (CUD)"),

    // ── 리크루팅 ──────────────────────────────────────────────────
    RECRUITMENT_NOTICE_WRITE("리크루팅 공고 / 문항 관리 (CUD)"),
    RECRUITMENT_NOTICE_READ("리크루팅 공고 / 문항 조회 (R)"),
    APPLICANT_CSV_READ("지원자 CSV 추출 (R)"),
    APPLICATION_DELETE_ALL("지원서 전체 삭제 (D)"),
    PRE_NOTIFICATION_READ("사전알림명단 조회 (R)"),
    PRE_NOTIFICATION_DELETE("사전알림명단 전체 삭제 (D)"),
    MEMBER_PROMOTION_WRITE("합격자 정회원 승격 (U)"),

    // ── 출결 관리 ─────────────────────────────────────────────────
    ATTENDANCE_BASE_WRITE("BASE Term 출결 관리 (CUD)"),
    ATTENDANCE_BASE_READ("BASE Term 출결 조회 (R)"),
    ATTENDANCE_ADV_WRITE("ADV Term 출결 관리 (CUD)"),
    ATTENDANCE_ADV_READ("ADV Term 출결 조회 (R)"),
    ATTENDANCE_STUDY_WRITE("스터디 출결 관리 (CUD)"),
    ATTENDANCE_STUDY_READ("스터디 출결 조회 (R)"),
    ATTENDANCE_EVENT_WRITE("행사 출결 관리 (CUD)"),
    ATTENDANCE_EVENT_READ("행사 출결 조회 (R)"),
    ATTENDANCE_SCORE_WRITE("출석 점수 집계 관리 (CUD)"),
    ATTENDANCE_SCORE_READ("출석 점수 집계 조회 (R)"),

    // ── 출결 입력 ─────────────────────────────────────────────────
    ATTENDANCE_ADV_ENTRY_READ("ADV 출결 입력탭 조회 (R)"),
    ATTENDANCE_ADV_ENTRY_WRITE("ADV 출결 입력탭 관리 (CUD)"),
    ATTENDANCE_ADV_ENTRY_OWN_GROUP_READ("담당 그룹 ADV 출결 입력탭 조회 (R)"),
    ATTENDANCE_ADV_ENTRY_OWN_GROUP_WRITE("담당 그룹 ADV 출결 입력탭 관리 (CU)"),
    ATTENDANCE_STUDY_ENTRY_READ("스터디 출결 입력탭 조회 (R)"),
    ATTENDANCE_STUDY_ENTRY_WRITE("스터디 출결 입력탭 관리 (CUD)"),
    ATTENDANCE_STUDY_ENTRY_OWN_GROUP_READ("담당 그룹 스터디 출결 입력탭 조회 (R)"),
    ATTENDANCE_STUDY_ENTRY_OWN_GROUP_WRITE("담당 그룹 스터디 출결 입력탭 관리 (CU)"),

    // ── HOST 계정과 점수 규칙 ─────────────────────────────────────
    HOST_ACCOUNT_WRITE("HOST 계정 발급 (CUD)"),
    HOST_ACCOUNT_READ("HOST 계정 조회 (R)"),
    SCORE_RULE_WRITE("점수 규칙 관리 (CUD)"),
    SCORE_RULE_READ("점수 규칙 조회 (R)"),

    // ── 서류 평가와 최종 합불 ─────────────────────────────────────
    EVALUATION_OWN_TRACK_WRITE("본인 부문 서류 평가 (CRUD)"),
    EVALUATION_ALL_TRACK_WRITE("다른 부문 서류 평가 (CRUD)"),
    FINAL_DECISION_WRITE("최종 합불 관리 (CUD)"),
    FINAL_DECISION_READ("최종 합불 조회 (R)");

    private final String displayName;

    Permission(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
