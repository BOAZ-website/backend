-- 시스템 계정(#223): 동아리원 명단. backend-docs erd/member.md "member 동아리원" 정의 그대로 만든다.
-- 19~27기 CSV 백필은 이 파일에 넣지 않고 릴리즈 때 실행한다(db/README.md "데이터 백필").
-- track·status는 Java enum이지만 값이 늘어날 수 있어 MySQL ENUM 대신 varchar + CHECK로 둔다.
-- version·created_at·updated_at의 DB 기본값은 SQL 백필이 이 컬럼을 생략해도 되게 둔다. 앱은 JPA가 값을 직접 넣는다.
CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `term` int NOT NULL,
  `track` varchar(20) NOT NULL,
  `name` varchar(255) NOT NULL,
  `name_suffix` varchar(2) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `university` varchar(100) DEFAULT NULL,
  `major` varchar(100) DEFAULT NULL,
  `source_user_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_source_user` (`source_user_id`),
  KEY `ix_member_identity` (`term`, `track`, `name`, `deleted_at`),
  KEY `ix_member_status` (`status`, `term`),
  CONSTRAINT `ck_member_track` CHECK (`track` IN ('ANALYSIS', 'VISUALIZATION', 'ENGINEERING')),
  CONSTRAINT `ck_member_status` CHECK (`status` IN ('ACTIVE', 'COMPLETED', 'NOT_COMPLETED', 'WITHDRAWN', 'EXPELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
