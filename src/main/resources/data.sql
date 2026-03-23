-- recruitment 임시 데이터
INSERT INTO recruitment (id, term, start_date, end_date, schedule, brochure_url, created_at, updated_at)
VALUES (1, 26, '2026-03-01 00:00:00', '2026-03-29 23:59:59', 
'[
  { "step": "서류 모집", "start": "2026-03-01", "end": "2026-03-29", "sequence": 1 },
  { "step": "서류 합격 발표", "start": "2026-04-04", "end": "2026-04-04", "sequence": 2 },
  { "step": "면접", "start": "2026-04-06", "end": "2026-04-07", "sequence": 3 },
  { "step": "최종 합격 발표", "start": "2026-04-10", "end": "2026-04-10", "sequence": 4 }
]', 'https://example.com/brochure.pdf', NOW(), NOW());

INSERT INTO recruitment (id, term, start_date, end_date, schedule, brochure_url, created_at, updated_at)
VALUES (2, 27, '2026-07-01 00:00:00', '2026-07-31 23:59:59', 
'[
  { "step": "서류 모집", "start": "2026-07-01", "end": "2026-07-31", "sequence": 1 },
  { "step": "서류 합격 발표", "start": "2026-08-04", "end": "2026-08-04", "sequence": 2 },
  { "step": "면접", "start": "2026-08-06", "end": "2026-08-07", "sequence": 3 },
  { "step": "최종 합격 발표", "start": "2026-08-10", "end": "2026-08-10", "sequence": 4 }
]', 'https://example.com/brochure.pdf', NOW(), NOW());

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

-- archive 임시 데이터
INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (1, 25, '프로젝트', '2024 BOAZ 컨퍼런스 추천시스템 발표', '자료연구팀', '분석', 'https://example.com/conf1.png', '{"slideshare":"https://slideshare.net/example1"}', '2024-08-10', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (2, 0, '프로젝트', '2024 BOAZ 컨퍼런스 우엥우엥우엥 발표', NULL, '분석', 'https://example.com/conf1.png', '{"slideshare":"https://slideshare.net/example1"}', '2024-08-10', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (3, 25, '프로젝트', '2024 BOAZ 데이터 파이프라인 발표', '자료연구팀', '엔지니어링', 'https://example.com/conf2.png', '{"slideshare":"https://slideshare.net/example2"}', '2024-08-10', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (4, 25, '기술블로그', 'Spring Boot 예외 처리 구조 정리', NULL, '엔지니어링', 'https://example.com/blog1.png', '{"medium":"https://medium.com/@boaz/example1"}', '2025-02-14', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (5, 25, '기술블로그', '데이터 분석 프로젝트 회고', NULL, '분석', 'https://example.com/blog2.png', '{"medium":"https://medium.com/@boaz/example2"}', '2025-01-20', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (6, 25, '활동사진', '250402 분석 7주차 Base세션', NULL, '분석', 'https://example.com/activity1.png', '{"instagram":"https://instagram.com/p/example1"}', '2025-04-02', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (7, 25, '활동사진', '25기 신입기수 OT', NULL, '전부문', 'https://example.com/activity2.png', '{"instagram":"https://instagram.com/p/example2"}', '2025-03-01', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (8, 26, '프로젝트', '2025 BOAZ 추천 시스템 고도화 발표', NULL, '엔지니어링', 'https://example.com/conf3.png', '{"slideshare":"https://slideshare.net/example3","github":"https://github.com/boaz/recommendation-system"}', '2025-08-12', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (9, 26, '프로젝트', '2025 BOAZ 실시간 데이터 처리 시스템 발표', '데이터엔지니어링팀', '엔지니어링', 'https://example.com/conf4.png', '{"slideshare":"https://slideshare.net/example4","github":"https://github.com/boaz/stream-processing"}', '2025-08-18', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (10, 26, '프로젝트', '2025 BOAZ 데이터 시각화 대시보드 발표', '데이터시각화팀', '시각화', 'https://example.com/conf5.png', '{"slideshare":"https://slideshare.net/example5","github":"https://github.com/boaz/dashboard-project"}', '2025-08-25', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (11, 26, '기술블로그', 'Kafka 기반 데이터 스트리밍 아키텍처 정리', NULL, '엔지니어링', 'https://example.com/blog3.png', '{"medium":"https://medium.com/@boaz/kafka-stream","github":"https://github.com/boaz/kafka-stream-example"}', '2025-03-01', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (12, 26, '기술블로그', 'Spark 기반 대용량 데이터 처리 경험 정리', NULL, '엔지니어링', 'https://example.com/blog4.png', '{"medium":"https://medium.com/@boaz/spark-processing","github":"https://github.com/boaz/spark-example"}', '2025-03-05', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (13, 26, '기술블로그', '추천 시스템 협업 필터링 구현 과정', NULL, '분석', 'https://example.com/blog5.png', '{"medium":"https://medium.com/@boaz/collaborative-filtering","github":"https://github.com/boaz/cf-recommendation"}', '2025-03-10', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (14, 26, '활동사진', '26기 데이터 분석 스터디 세션', NULL, '분석', 'https://example.com/activity3.png', '{"instagram":"https://instagram.com/p/example3","youtube":"https://youtube.com/watch?v=analysis-session"}', '2025-04-05', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (15, 26, '활동사진', '26기 데이터 엔지니어링 워크샵', NULL, '엔지니어링', 'https://example.com/activity4.png', '{"instagram":"https://instagram.com/p/example4","youtube":"https://youtube.com/watch?v=engineering-workshop"}', '2025-04-10', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (16, 26, '활동사진', '26기 데이터 시각화 세션', NULL, '시각화', 'https://example.com/activity5.png', '{"instagram":"https://instagram.com/p/example5","youtube":"https://youtube.com/watch?v=visualization-session"}', '2025-04-15', NOW(), NOW());
-- applicant 임시 데이터
INSERT INTO applicants (recruitment_id, track, name, email, phone, university, major, minor_double_major, last_semester, military_status, birth_date, graduation_date, grad_school_plan, created_at, updated_at)
VALUES (1, '엔지니어링', '테스트1', 'string@example', '01012345678', 'university', 'major', '["minor_double_major"]', 7, '필_또는_면제', '2002-01-01', '2026-08', false, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicants (recruitment_id, track, name, email, phone, university, major, minor_double_major, last_semester, military_status, birth_date, graduation_date, grad_school_plan, created_at, updated_at)
VALUES (1, '엔지니어링', '테스트1', 'string@example', '01012345678', 'university', 'major', '["minor_double_major"]', 7, '필_또는_면제', '2002-01-01', '2026-08', false, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicants (recruitment_id, track, name, email, phone, university, major, minor_double_major, last_semester, military_status, birth_date, graduation_date, grad_school_plan, created_at, updated_at)
VALUES (1, '분석', '테스트2', 'string22@example', '01012345688', 'university', 'major', '["minor_double_major"]', 7, '필_또는_면제', '2002-01-01', '2026-08', false, '2026-03-14 22:21:18', '2026-03-14 22:21:18');

INSERT INTO applicants (recruitment_id, track, name, email, phone, university, major, minor_double_major, last_semester, military_status, birth_date, graduation_date, grad_school_plan, created_at, updated_at)
VALUES (1, '시각화', '테스트3', 'string33@example', '01012345622', 'university', 'major', '["minor_double_major"]', 7, '필_또는_면제', '2002-01-01', '2026-08', false, '2026-03-14 22:21:58', '2026-03-14 22:21:58');

-- applicant_answer 임시 데이터 (테스트1 첫 번째 제출 - id: 1)
INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, '공통1', '공통1답변', null, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, '공통2', '공통2답변', null, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, '엔지니어링1', null, '{"데이터베이스": "경험 없음", "서버 및 클라우드 서비스": "경험 없음"}', '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, '엔지니어링2', '엔지2답변', null, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, '공통5', '공통5답변\nurl', null, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

-- applicant_answer 임시 데이터 (테스트1 두 번째 제출 - id: 2)
INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, '공통1', '공통1답변11111', null, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, '공통2', '공통2답변22222', null, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, '엔지니어링1', null, '{"데이터베이스": "경험 없음", "서버 및 클라우드 서비스": "경험 없음"}', '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, '엔지니어링2', '엔지2답변2222', null, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, '공통5', '공통5답변5555\nurl', null, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

-- applicant_answer 임시 데이터 (테스트2 분석 - id: 3)
INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, '공통1', '공통1답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, '공통2', '공통2답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, '분석1', '분석1답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, '분석2', '분석2답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, '공통5', '공통5답변\nurl', null, NOW(), NOW());

-- applicant_answer 임시 데이터 (테스트3 시각화 - id: 4)
INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, '공통1', '공통1답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, '공통2', '공통2답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, '시각화1', null, '{"Python": "경험 없음", "Tableau": "경험 없음"}', NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, '시각화2', '시각화2답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, '공통5', '공통5답변\nurl', null, NOW(), NOW());
