-- 권한 개편(#204): 그룹 리더 관계. dev의 옛 구조 엔티티(AdminGroupLeader) 그대로 만든다.
-- 2026-11-08 BE 회의에서 새 구조(enrollment_id 기준, 장 변경 이력)로 바꾸기로 했다.
-- 출결 기본 테이블 PR에서 엔티티와 함께 바꾼다. 첫 prod 배포 전이면 이 파일을 직접 고치고,
-- 배포 후면 새 파일에서 DROP 후 새 구조로 CREATE한다. 이 테이블에 쓰는 코드는 없다.
CREATE TABLE `admin_group_leader` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `admin_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_leader` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
