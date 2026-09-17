package com.boaz.backend.global.security.authz;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ScopeGuard {

    private final EffectivePermissions effectivePermissions;

    /**
     * 축 ① 부문 — 서류 평가. 전 부문 권한자는 대상 부문과 무관하게 통과하고, 그렇지 않으면 본인 부문만 통과한다.
     *
     * @param targetTrack 평가 대상 지원자의 부문
     */
    public void checkTrack(Admin admin, Track targetTrack) {
        Set<Permission> permissions = effectivePermissions.of(admin);
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
     */
    public void checkAccountWrite(Admin admin, Long targetAdminId) {
        Set<Permission> permissions = effectivePermissions.of(admin);
        if (permissions.contains(Permission.ADMIN_ACCOUNT_WRITE)) {
            return;
        }
        if (!admin.getId().equals(targetAdminId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }
}
