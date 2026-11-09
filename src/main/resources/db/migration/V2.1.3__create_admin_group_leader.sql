-- 권한 개편(#204): 그룹 리더 관계. dev의 옛 구조 엔티티(AdminGroupLeader) 그대로 만든다.
-- 2026-11-08 BE 회의에서 새 구조(enrollment_id 기준, 장 변경 이력)로 바꾸기로 했다. 출결 기본 테이블 PR에서 바꾼다.
-- 이 테이블에 쓰는 코드는 없다. 바꿀 때는 이 파일이 공유 DB(prod, dev)에 실행됐는지에 따라 방법이 다르다.
-- - 어느 공유 DB에도 실행되기 전: 이 파일을 지우고, 새 구조 CREATE는 참조하는 테이블(member_enrollment,
--   activity_group 등)을 만드는 파일보다 뒤 버전에 새로 둔다. 이 파일의 CREATE 본문만 바꾸면 빈 DB에서
--   참조 대상보다 먼저 실행되어 실패한다.
-- - 공유 DB에 실행된 뒤: 이 파일은 고치지 않는다. 롤백 대상 앱(옛 AdminGroupLeader 엔티티)이 Hibernate validate에서
--   실패하지 않도록, 옛 엔티티 제거를 먼저 배포한 뒤 테이블을 바꾸거나 옛 앱이 요구하는 구조를 유지하는 전환 절차를 쓴다.
CREATE TABLE `admin_group_leader` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `admin_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_leader` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
