package com.boaz.backend.domain.admin.entity;

import com.boaz.backend.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "이 admin이 이 그룹(ADV팀/스터디)의 리더다" 한 줄이고, 겸임이 있으므로
 * 한 admin이 여러 행을 가질 수 있다(스터디 2개 = 행 2개). 유일성은 <b>그룹당 리더 1명</b>이다.
 *
 * <p><b>옛 구조다. 새 코드에서 쓰지 않는다.</b> 2026-11-08 BE 회의에서 그룹 리더 관계를 새 구조
 * ({@code enrollment_id} 기준, 장 변경 이력 {@code assigned_at}·{@code revoked_at})로 바꾸기로 했고,
 * 출결 기본 테이블 작업에서 이 엔티티와 테이블을 함께 바꾼다. 그때까지는 prod {@code validate}를 통과시키려고
 * 테이블만 만들어 둔다({@code V2.1.3__create_admin_group_leader.sql}, 바꾸는 방법은 그 파일 주석 참고).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin_group_leader",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_group_leader", columnNames = {"group_id"}))
public class AdminGroupLeader extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    /** {@code activity_group.id}. 테이블이 아직 없어 FK를 걸지 않는다. */
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Builder
    public AdminGroupLeader(Long adminId, Long groupId) {
        this.adminId = adminId;
        this.groupId = groupId;
    }
}
