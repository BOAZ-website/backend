-- 시스템 계정(#223): 관리자 계정이 소유 동아리원을 가리킨다. backend-docs erd/admin.md "배포 순서" 2단계.
-- 기존 계정은 아직 member와 연결되지 않았으므로 NULL 허용으로 추가한다.
-- 삭제된 계정까지 백필한 뒤 다음 배포에서 NOT NULL로 바꾼다(같은 문서 6단계).
-- 컬럼 추가와 FK를 ALTER 한 문장으로 묶어, 컬럼만 생기고 FK가 빠진 상태가 남지 않게 한다.
ALTER TABLE `admin`
  ADD COLUMN `member_id` bigint DEFAULT NULL,
  ADD CONSTRAINT `fk_admin_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`);
