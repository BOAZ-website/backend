-- 권한 개편(#206, #207, #211, #213): 계정별 권한 조정(기본 권한 대비 차이만 저장)
-- permission은 Permission enum 값이 자주 늘어나므로 MySQL ENUM 대신 varchar로 둔다.
-- admin_id, granted_by는 엔티티가 연관관계 없이 id만 들고 있어 FK를 걸지 않는다.
CREATE TABLE `admin_permission_override` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint NOT NULL,
  `permission` varchar(64) NOT NULL,
  `effect` varchar(8) NOT NULL,
  `granted_by` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_permission` (`admin_id`,`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
