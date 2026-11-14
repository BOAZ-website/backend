-- V1: 2026-11-09 기준 prod 스키마 (main 엔티티 기준)
-- prod에서 mysqldump --no-data로 받은 결과에서 덤프 설정 줄, DROP TABLE, AUTO_INCREMENT 값을 지우고
-- 외래 키 순서에 맞게 테이블 순서만 바꿨다. 컬럼·인덱스·제약조건 이름은 prod 그대로다.
-- prod에는 이 파일을 실행하지 않는다. 첫 배포 때 baseline으로 "V1까지 적용됨"만 기록한다.

CREATE TABLE `admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('SUPER','TEAM') NOT NULL,
  `team_name` varchar(20) NOT NULL,
  `term` int NOT NULL,
  `track` enum('ALL','ANALYSIS','ENGINEERING','VISUALIZATION') NOT NULL,
  `username` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgfn44sntic2k93auag97juyij` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `major` varchar(255) DEFAULT NULL,
  `member_type` enum('MEMBER','OUTSIDER') NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `nickname` varchar(100) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `provider` varchar(20) NOT NULL,
  `provider_id` varchar(255) NOT NULL,
  `term` int DEFAULT NULL,
  `track` enum('ALL','ANALYSIS','ENGINEERING','VISUALIZATION') DEFAULT NULL,
  `university` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `minor_double_major` json DEFAULT NULL,
  `last_semester` int DEFAULT NULL,
  `military_status` varchar(50) DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `graduation_date` varchar(7) DEFAULT NULL,
  `grad_school_plan` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKcbysvpk95086ud4n4g6mkspai` (`provider`,`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recruitment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `brochure_url` text,
  `end_date` datetime(6) NOT NULL,
  `schedule` json NOT NULL,
  `start_date` datetime(6) NOT NULL,
  `term` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf04w9g5lvyp6pd6vpg04smdf6` (`term`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `application_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category` enum('ANALYSIS','COMMON','ENGINEERING','VISUALIZATION') NOT NULL,
  `content` text NOT NULL,
  `is_required` bit(1) NOT NULL,
  `limit_length` int DEFAULT NULL,
  `metadata` json DEFAULT NULL,
  `order_num` int NOT NULL,
  `type` enum('TABLE','TEXT') NOT NULL,
  `recruitment_id` bigint NOT NULL,
  `label` varchar(20) DEFAULT NULL,
  `description` text,
  PRIMARY KEY (`id`),
  KEY `FKllob166t5ayt0hqj73iujgqx3` (`recruitment_id`),
  CONSTRAINT `FKllob166t5ayt0hqj73iujgqx3` FOREIGN KEY (`recruitment_id`) REFERENCES `recruitment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `applicants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `grad_school_plan` bit(1) DEFAULT NULL,
  `graduation_date` varchar(7) DEFAULT NULL,
  `last_semester` int DEFAULT NULL,
  `major` varchar(100) DEFAULT NULL,
  `military_status` enum('COMPLETED_OR_EXEMPT','NOT_COMPLETED') DEFAULT NULL,
  `minor_double_major` json DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` enum('DRAFT','SUBMITTED') NOT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `track` enum('ALL','ANALYSIS','ENGINEERING','VISUALIZATION') DEFAULT NULL,
  `university` varchar(100) DEFAULT NULL,
  `recruitment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `final_decision` varchar(20) NOT NULL DEFAULT 'PENDING',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8dcjpq7ywwkgrsen3kr8yfj57` (`user_id`),
  KEY `FKon8h9o4c39tghomcu3y1hktjo` (`recruitment_id`),
  CONSTRAINT `FKon8h9o4c39tghomcu3y1hktjo` FOREIGN KEY (`recruitment_id`) REFERENCES `recruitment` (`id`),
  CONSTRAINT `FKs48x9ywgx7l6mg1egokmys0gp` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `applicant_answer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `answer_json` json DEFAULT NULL,
  `answer_text` text,
  `applicant_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKf52klyq95y3rnn17s2ekdreka` (`applicant_id`),
  KEY `FKtl3nwa9tp9x7p0xvbowmhfy86` (`question_id`),
  CONSTRAINT `FKf52klyq95y3rnn17s2ekdreka` FOREIGN KEY (`applicant_id`) REFERENCES `applicants` (`id`),
  CONSTRAINT `FKtl3nwa9tp9x7p0xvbowmhfy86` FOREIGN KEY (`question_id`) REFERENCES `application_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `applicant_eval` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applicant_id` bigint NOT NULL,
  `admin_id` bigint NOT NULL,
  `decision` varchar(20) NOT NULL DEFAULT 'PENDING',
  `score` int DEFAULT NULL,
  `memo` text,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `interview_question` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_applicant_eval_applicant_admin` (`applicant_id`,`admin_id`),
  KEY `fk_applicant_eval_admin` (`admin_id`),
  CONSTRAINT `fk_applicant_eval_admin` FOREIGN KEY (`admin_id`) REFERENCES `admin` (`id`),
  CONSTRAINT `fk_applicant_eval_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `applicants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category` enum('ACTIVITY','BLOG','PROJECT') NOT NULL,
  `content_date` date DEFAULT NULL,
  `image_url` text NOT NULL,
  `links` json NOT NULL,
  `team_name` varchar(100) DEFAULT NULL,
  `term` int NOT NULL,
  `title` varchar(255) NOT NULL,
  `track` enum('ALL','ANALYSIS','ENGINEERING','VISUALIZATION') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `curriculum` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `curriculum_steps` json DEFAULT NULL,
  `track` enum('ALL','ANALYSIS','ENGINEERING','VISUALIZATION') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_curriculum_track` (`track`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `faq` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `answer` text NOT NULL,
  `category` enum('ACTIVITY','ETC','RECRUITMENT') NOT NULL,
  `order_num` int NOT NULL,
  `question` text NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_faq_category_order_num` (`category`,`order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `refresh_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL,
  `account_type` enum('ADMIN','USER') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token` text NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4u2jqvxf8gakx1s5o2gq6dlsd` (`account_type`,`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` text,
  `image_url` text,
  `name` varchar(255) DEFAULT NULL,
  `term` int DEFAULT NULL,
  `track` enum('ALL','ANALYSIS','ENGINEERING','VISUALIZATION') DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr9kv3a5fkv7vnvu8bbpulavmk` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
