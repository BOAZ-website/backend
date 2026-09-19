package com.boaz.backend.global.security.authz;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 3층 — 대상을 봐야만 갈리는 범위 판정. 2층({@code @PreAuthorize})은 permission 보유 여부만 보고 DB를
 * 조회하지 않으며, "누구를/무엇을" 대상으로 하느냐는 전부 여기로 모인다.
 *
 * <p><b>분기를 서비스 본문에 쓰지 않는다.</b> "전체 권한이면 통과"라는 규칙이 호출부마다 반복되지 않도록 한다.
 * 서비스는 {@code check*}를 부르기만 한다.
 *
 * <p><b>넓은 permission을 먼저 본다.</b> 순서가 뒤집히면 전체 권한자가 "본인 부문 아님"으로 거부된다.
 *
 * <p><b>권한을 다시 계산하지 않고 받는다.</b> 유효 권한은 매 요청 {@code AdminUserDetailsService}가
 * 이미 계산해 {@code AdminUserDetails}에 담아 두므로, 여기서 {@code EffectivePermissions}를 또 부르면
 * 같은 오버라이드 조회가 한 번 더 나간다. 같은 요청 안에서 주체의 권한이 바뀌는 경로도 없다 —
 * 오버라이드 삭제는 <b>대상</b> 계정에 대해, 그것도 이 판정을 통과한 뒤에 일어난다.
 *
 * <p>인자는 {@code AdminUserDetails}가 아니라 {@code Set<Permission>}이다. 3층 판정에 필요한 것은
 * 권한 집합뿐이고, 이 클래스가 Spring Security의 principal 타입을 알 이유가 없다.
 *
 * <p>판정 축은 셋인데 이 클래스가 갖는 것은 둘이다.
 * <ul>
 *   <li>축 ① 부문 — {@link #checkTrack}</li>
 *   <li>축 ② 담당 그룹 — <b>이번 범위 밖.</b> {@code admin_group_leader ⋈ activity_group}으로 그룹 타입까지
 *       확인하고 주차 경과 조건이 딸리는 판정인데, 출결 도메인과 {@code activity_group}이 아직 없어 호출부가
 *       없다 </li>
 *   <li>축 ③ 본인 — {@link #checkAccountWrite}</li>
 * </ul>
 */
@Component
public class ScopeGuard {

    /**
     * 축 ① 부문 — 서류 평가. 전 부문 권한자는 대상 부문과 무관하게 통과하고, 그렇지 않으면 본인 부문만 통과한다.
     *
     * @param permissions 주체의 유효 권한. {@code AdminUserDetails.getPermissions()}를 그대로 넘긴다
     * @param targetTrack 평가 대상 지원자의 부문
     */
    public void checkTrack(Admin admin, Set<Permission> permissions, Track targetTrack) {
        if (permissions.contains(Permission.EVALUATION_ALL_TRACK_WRITE)) {
            return;
        }
        if (admin.getTrack() != targetTrack) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 축 ③ 본인 — 계정 정보 수정 / 비밀번호 변경. 2층에서
     * {@code hasAnyAuthority('ADMIN_ACCOUNT_SELF_WRITE','ADMIN_ACCOUNT_WRITE')}로 통과시킨 뒤,
     * 어느 쪽으로 통과했는지를 여기서 가른다 — 타 계정 권한이 없으면 본인 계정일 때만 통과한다.
     *
     * @param permissions 주체의 유효 권한. {@code AdminUserDetails.getPermissions()}를 그대로 넘긴다
     */
    public void checkAccountWrite(Admin admin, Set<Permission> permissions, Long targetAdminId) {
        if (permissions.contains(Permission.ADMIN_ACCOUNT_WRITE)) {
            return;
        }
        if (!admin.getId().equals(targetAdminId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 축 ③ 본인 — 계정 단건 조회. 2층에서
     * {@code hasAnyAuthority('ADMIN_ACCOUNT_SELF_READ','ADMIN_ACCOUNT_READ')}로 통과시킨 뒤,
     * 어느 쪽으로 통과했는지를 여기서 가른다 — 계정 조회 권한이 없으면 본인 계정일 때만 통과한다.
     * {@code GET /accounts/me}에는 붙이지 않는다. 주체 자신을 돌려주는 엔드포인트라 대상 판정이 없다.
     *
     * @param permissions 주체의 유효 권한. {@code AdminUserDetails.getPermissions()}를 그대로 넘긴다
     */
    public void checkAccountRead(Admin admin, Set<Permission> permissions, Long targetAdminId) {
        if (permissions.contains(Permission.ADMIN_ACCOUNT_READ)) {
            return;
        }
        if (!admin.getId().equals(targetAdminId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }
}
