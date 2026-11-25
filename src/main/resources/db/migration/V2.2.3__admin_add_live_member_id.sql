-- 시스템 계정(#223): 회원 한 명당 삭제되지 않은 계정은 역할과 관계없이 최대 하나다.
-- 삭제된 계정은 member_id를 보존하고 live_member_id만 NULL이 되어 UNIQUE에서 빠진다.
-- 엔티티는 이 컬럼을 매핑하지 않는다(값은 DB가 계산하고 앱이 읽지 않는다).
ALTER TABLE `admin`
  ADD COLUMN `live_member_id` bigint GENERATED ALWAYS AS (IF(`deleted_at` IS NULL, `member_id`, NULL)) STORED,
  ADD UNIQUE KEY `uk_admin_live_member` (`live_member_id`);
