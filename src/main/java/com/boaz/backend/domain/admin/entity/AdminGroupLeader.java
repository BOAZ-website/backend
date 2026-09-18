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
 * {@code group_id}의 FK가 아직 없다: {@code activity_group} 테이블이
 * 출석 도메인과 함께 생길 때 {@code ALTER}로 FK 2개를 건다.
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
