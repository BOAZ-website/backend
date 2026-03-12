-- recruitment 임시 데이터
INSERT INTO recruitment (id, term, start_date, end_date, brochure_url, created_at, updated_at)
VALUES (1, 26, '2026-03-01 00:00:00', '2026-03-29 23:59:59', 'https://example.com/brochure.pdf', NOW(), NOW());

INSERT INTO recruitment (id, term, start_date, end_date, brochure_url, created_at, updated_at)
VALUES (2, 27, '2026-07-01 00:00:00', '2026-07-31 23:59:59', 'https://example.com/brochure.pdf', NOW(), NOW());

-- application_question 임시 데이터
INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('공통1', 1, '공통', 'TEXT', '지원 동기는 무엇인가요? (500자 이내)', null, 500, 1, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('공통2', 1, '공통', 'TEXT', '본인을 나타낼 수 있는 기존의 활동 경험 (500자 이내)', null, 500, 2, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('공통5', 1, '공통', 'TEXT', '추가적으로 자신의 활동 중에서 특히 어필하고 싶은 프로젝트가 있다면.. (500자 이내)', null, 500, 99, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('시각화1', 1, '시각화', 'TABLE', '데이터 시각화 관련 TOOL 활용 경험', '{"rows":["Tableau", "Python"], "columns":["경험 없음", "관련 프로젝트 경험 있음"]}', null, 10, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('시각화2', 1, '시각화', 'TEXT', '본인이 진행했던 시각화를 통해 인사이트를 도출한 경험.. (700자 이내)', null, 700, 11, true, NOW(), NOW());


INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('엔지니어링1', 1, '엔지니어링', 'TABLE', '데이터 엔지니어링 관련 경험', '{"rows":["데이터베이스", "서버 및 클라우드 서비스"], "columns":["경험 없음", "관련 프로젝트 경험 있음"]}', null, 10, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('엔지니어링2', 1, '엔지니어링', 'TEXT', '데이터 엔지니어링 분야 중 관심있는 세부 분야와 해당 분야와 관련된 경험 및 활동.. (700자 이내)', null, 700, 11, true, NOW(), NOW());


INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('분석1', 1, '분석', 'TEXT', '[빅데이터 / 인공지능 / 머신러닝 / 통계 및 수학] 관련 수강 과목 혹은 세미나 경험.. (300자 이내)', null, 300, 10, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('분석2', 1, '분석', 'TEXT', '본인이 진행했던 [머신러닝 / 딥러닝 / 데이터분석] 관련 프로젝트를 소개.. (700자 이내)', null, 700, 11, true, NOW(), NOW());