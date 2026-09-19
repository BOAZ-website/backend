package com.boaz.backend.domain.admin.entity;

import com.boaz.backend.global.security.authz.Permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 계정별 권한 오버라이드. 기본 세트({@code DefaultPermissions}) <b>대비 차이</b>만 담는 행이다.
 * role·teamName이 바뀌면 그 계정의 행을 전부 지운다.
 *
 * <p><b>{@code BaseEntity}를 상속하지 않는다.</b> 쓰기가 전삭제 + 재삽입이라 {@code updated_at}이 죽은 컬럼이
 * 되고, 남길 감사 정보는 {@code granted_by}와 짝인 {@code created_at} 하나뿐이다. PK는 대리 키
 * {@code id}를 둔다 — prod가 {@code ddl-auto: validate}라 PK가 필수다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin_permission_override",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_admin_permission", columnNames = {"admin_id", "permission"}))
public class AdminPermissionOverride {

    public enum Effect { GRANT, REVOKE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Effect effect;

    /** 이 오버라이드를 부여한 admin id. 감사 정보라 수정하지 않는다. */
    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AdminPermissionOverride(Long adminId, Permission permission, Effect effect, Long grantedBy) {
        this.adminId = adminId;
        this.permission = permission;
        this.effect = effect;
        this.grantedBy = grantedBy;
        this.createdAt = LocalDateTime.now();
    }
}
