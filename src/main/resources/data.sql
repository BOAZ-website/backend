-- 외래 키 체크 해제 (실행 오류 방지)
SET FOREIGN_KEY_CHECKS = 0;

-- 모든 테이블 초기화
TRUNCATE recruitment;
TRUNCATE application_question;
TRUNCATE archive;
TRUNCATE applicants;
TRUNCATE applicant_answer;
TRUNCATE curriculum;
TRUNCATE faq;
TRUNCATE review;

-- 외래 키 체크 재활성화
SET FOREIGN_KEY_CHECKS = 1;

-- 임시 데이터

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
VALUES ('COMMON1', 1, 'COMMON', 'TEXT', 'RECRUITMENT 동기는 무엇인가요? (500자 이내)', null, 500, 1, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('COMMON2', 1, 'COMMON', 'TEXT', '본인을 나타낼 수 있는 기존의 ACTIVITY 경험 (500자 이내)', null, 500, 2, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('COMMON5', 1, 'COMMON', 'TEXT', '추가적으로 자신의 ACTIVITY 중에서 특히 어필하고 싶은 PROJECT가 있다면.. (500자 이내)', null, 500, 99, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('VISUALIZATION1', 1, 'VISUALIZATION', 'TABLE', '데이터 VISUALIZATION 관련 TOOL 활용 경험', '{"rows":["Tableau", "Python"], "columns":["경험 없음", "관련 PROJECT 경험 있음"]}', null, 10, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('VISUALIZATION2', 1, 'VISUALIZATION', 'TEXT', '본인이 진행했던 VISUALIZATION를 통해 인사이트를 도출한 경험.. (700자 이내)', null, 700, 11, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('ENGINEERING1', 1, 'ENGINEERING', 'TABLE', '데이터 ENGINEERING 관련 경험', '{"rows":["데이터베이스", "서버 및 클라우드 서비스"], "columns":["경험 없음", "관련 PROJECT 경험 있음"]}', null, 10, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('ENGINEERING2', 1, 'ENGINEERING', 'TEXT', '데이터 ENGINEERING 분야 중 관심있는 세부 분야와 해당 분야와 관련된 경험 및 ACTIVITY.. (700자 이내)', null, 700, 11, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('ANALYSIS1', 1, 'ANALYSIS', 'TEXT', '[빅데이터 / 인공지능 / 머신러닝 / 통계 및 수학] 관련 수강 과목 혹은 세미나 경험.. (300자 이내)', null, 300, 10, true, NOW(), NOW());

INSERT INTO application_question (id, recruitment_id, category, type, content, metadata, limit_length, order_num, is_required, created_at, updated_at)
VALUES ('ANALYSIS2', 1, 'ANALYSIS', 'TEXT', '본인이 진행했던 [머신러닝 / 딥러닝 / 데이터ANALYSIS] 관련 PROJECT를 소개.. (700자 이내)', null, 700, 11, true, NOW(), NOW());

-- archive 임시 데이터
INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (1, 25, 'PROJECT', '사용자 맞춤형 뭐뭐뭐뭐 추천시스템 발표', '자료연구팀', 'ANALYSIS', 'https://example.com/conf1.png', '{"slideshare":"https://slideshare.net/example1"}', '2024-08-10', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (2, 0, 'PROJECT', '누구구구의 뭐뭐뭐뭐 오오오오우 발표', NULL, 'ANALYSIS', 'https://example.com/conf1.png', '{"slideshare":"https://slideshare.net/example1"}', NULL, NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (3, 25, 'PROJECT', '무엇무엇 데이터 파이프라인 발표', '자료연구팀', 'ENGINEERING', 'https://example.com/conf2.png', '{"slideshare":"https://slideshare.net/example2"}', '2024-08-10', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (4, 25, 'BLOG', 'Spring Boot 예외 처리 구조 정리', NULL, 'ENGINEERING', 'https://example.com/blog1.png', '{"medium":"https://medium.com/@boaz/example1"}', '2025-02-14', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (5, 25, 'BLOG', '데이터 ANALYSIS PROJECT 회고', NULL, 'ANALYSIS', 'https://example.com/blog2.png', '{"medium":"https://medium.com/@boaz/example2"}', '2025-01-20', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (6, 25, 'ACTIVITY', '250402 ANALYSIS 7주차 Base세션', NULL, 'ANALYSIS', 'https://example.com/activity1.png', '{"instagram":"https://instagram.com/p/example1"}', '2025-04-02', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (7, 25, 'ACTIVITY', '25기 신입기수 OT', NULL, 'ALL', 'https://example.com/activity2.png', '{"instagram":"https://instagram.com/p/example2"}', '2025-03-01', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (8, 26, 'PROJECT', '추천 시스템 고도화 땡땡 발표', NULL, 'ENGINEERING', 'https://example.com/conf3.png', '{"slideshare":"https://slideshare.net/example3","github":"https://github.com/boaz/recommendation-system"}', '2025-08-12', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (9, 26, 'PROJECT', '실시간 데이터 처리 뭐뭐뭐뭐 시스템 발표', '데이터ENGINEERING팀', 'ENGINEERING', 'https://example.com/conf4.png', '{"slideshare":"https://slideshare.net/example4","github":"https://github.com/boaz/stream-processing"}', '2025-08-18', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (10, 26, 'PROJECT', '데이터 VISUALIZATION 대시보드 블라블라 발표', '데이터VISUALIZATION팀', 'VISUALIZATION', 'https://example.com/conf5.png', '{"slideshare":"https://slideshare.net/example5","github":"https://github.com/boaz/dashboard-project"}', '2025-08-25', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (11, 26, 'BLOG', 'Kafka 기반 데이터 스트리밍 아키텍처 정리', NULL, 'ENGINEERING', 'https://example.com/blog3.png', '{"medium":"https://medium.com/@boaz/kafka-stream","github":"https://github.com/boaz/kafka-stream-example"}', '2025-03-01', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (12, 26, 'BLOG', 'Spark 기반 대용량 데이터 처리 경험 정리', NULL, 'ENGINEERING', 'https://example.com/blog4.png', '{"medium":"https://medium.com/@boaz/spark-processing","github":"https://github.com/boaz/spark-example"}', '2025-03-05', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (13, 26, 'BLOG', '추천 시스템 협업 필터링 구현 과정', NULL, 'ANALYSIS', 'https://example.com/blog5.png', '{"medium":"https://medium.com/@boaz/collaborative-filtering","github":"https://github.com/boaz/cf-recommendation"}', '2025-03-10', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (14, 26, 'ACTIVITY', '26기 데이터 ANALYSIS 스터디 세션', NULL, 'ANALYSIS', 'https://example.com/activity3.png', '{"instagram":"https://instagram.com/p/example3","youtube":"https://youtube.com/watch?v=analysis-session"}', '2025-04-05', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (15, 26, 'ACTIVITY', '26기 데이터 ENGINEERING 워크샵', NULL, 'ENGINEERING', 'https://example.com/activity4.png', '{"instagram":"https://instagram.com/p/example4","youtube":"https://youtube.com/watch?v=engineering-workshop"}', '2025-04-10', NOW(), NOW());

INSERT INTO archive (id, term, category, title, team_name, track, image_url, links, content_date, created_at, updated_at) 
VALUES (16, 26, 'ACTIVITY', '26기 데이터 VISUALIZATION 세션', NULL, 'VISUALIZATION', 'https://example.com/activity5.png', '{"instagram":"https://instagram.com/p/example5","youtube":"https://youtube.com/watch?v=visualization-session"}', '2025-04-15', NOW(), NOW());

-- applicant 임시 데이터
INSERT INTO applicants (id, recruitment_id, track, name, email, phone, university, major, minor_double_major, last_semester, military_status, birth_date, graduation_date, grad_school_plan, created_at, updated_at)
VALUES (1, 1, 'ENGINEERING', '테스트1', 'string@example', '01012345678', 'university', 'major', '["minor_double_major"]', 7, 'COMPLETED_OR_EXEMPT', '2002-01-01', '2026-08', false, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicants (id, recruitment_id, track, name, email, phone, university, major, minor_double_major, last_semester, military_status, birth_date, graduation_date, grad_school_plan, created_at, updated_at)
VALUES (2, 1, 'ENGINEERING', '테스트1', 'string@example', '01012345678', 'university', 'major', '["minor_double_major"]', 7, 'COMPLETED_OR_EXEMPT', '2002-01-01', '2026-08', false, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicants (id, recruitment_id, track, name, email, phone, university, major, minor_double_major, last_semester, military_status, birth_date, graduation_date, grad_school_plan, created_at, updated_at)
VALUES (3, 1, 'ANALYSIS', '테스트2', 'string22@example', '01012345688', 'university', 'major', '["minor_double_major"]', 7, 'COMPLETED_OR_EXEMPT', '2002-01-01', '2026-08', false, '2026-03-14 22:21:18', '2026-03-14 22:21:18');

INSERT INTO applicants (id, recruitment_id, track, name, email, phone, university, major, minor_double_major, last_semester, military_status, birth_date, graduation_date, grad_school_plan, created_at, updated_at)
VALUES (4, 1, 'VISUALIZATION', '테스트3', 'string33@example', '01012345622', 'university', 'major', '["minor_double_major"]', 7, 'COMPLETED_OR_EXEMPT', '2002-01-01', '2026-08', false, '2026-03-14 22:21:58', '2026-03-14 22:21:58');

-- applicant_answer 임시 데이터 (테스트1 첫 번째 제출 - id: 1)
INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, 'COMMON1', 'COMMON1답변', null, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, 'COMMON2', 'COMMON2답변', null, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, 'ENGINEERING1', null, '{"데이터베이스": "경험 없음", "서버 및 클라우드 서비스": "경험 없음"}', '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, 'ENGINEERING2', '엔지2답변', null, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (1, 'COMMON5', 'COMMON5답변\nurl', null, '2026-03-14 22:19:27', '2026-03-14 22:19:27');

-- applicant_answer 임시 데이터 (테스트1 두 번째 제출 - id: 2)
INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, 'COMMON1', 'COMMON1답변11111', null, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, 'COMMON2', 'COMMON2답변22222', null, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, 'ENGINEERING1', null, '{"데이터베이스": "경험 없음", "서버 및 클라우드 서비스": "경험 없음"}', '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, 'ENGINEERING2', '엔지2답변2222', null, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (2, 'COMMON5', 'COMMON5답변5555\nurl', null, '2026-03-14 22:38:15', '2026-03-14 22:38:15');

-- applicant_answer 임시 데이터 (테스트2 ANALYSIS - id: 3)
INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, 'COMMON1', 'COMMON1답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, 'COMMON2', 'COMMON2답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, 'ANALYSIS1', 'ANALYSIS1답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, 'ANALYSIS2', 'ANALYSIS2답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (3, 'COMMON5', 'COMMON5답변\nurl', null, NOW(), NOW());

-- applicant_answer 임시 데이터 (테스트3 VISUALIZATION - id: 4)
INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, 'COMMON1', 'COMMON1답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, 'COMMON2', 'COMMON2답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, 'VISUALIZATION1', null, '{"Python": "경험 없음", "Tableau": "경험 없음"}', NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, 'VISUALIZATION2', 'VISUALIZATION2답변', null, NOW(), NOW());

INSERT INTO applicant_answer (applicant_id, question_id, answer_text, answer_json, created_at, updated_at)
VALUES (4, 'COMMON5', 'COMMON5답변\nurl', null, NOW(), NOW());

-- Curriculum 임시데이터
INSERT INTO curriculum (track, curriculum_steps, created_at, updated_at) VALUES
('ANALYSIS', '[
  {"step": 1, "title": "데이터 ANALYSIS 기초", "description": "통계 기초, 파이썬 기초"},
  {"step": 2, "title": "탐색적 데이터 ANALYSIS", "description": "Pandas, Numpy 활용"},
  {"step": 3, "title": "머신러닝 기초", "description": "Scikit-learn 활용"},
  {"step": 4, "title": "딥러닝 기초", "description": "TensorFlow, PyTorch 기초"},
  {"step": 5, "title": "PROJECT", "description": "실전 ANALYSIS PROJECT 수행"}
]', NOW(), NOW());

INSERT INTO curriculum (track, curriculum_steps, created_at, updated_at) VALUES
('VISUALIZATION', '[
  {"step": 1, "title": "VISUALIZATION 기초", "description": "Matplotlib, Seaborn 기초"},
  {"step": 2, "title": "대시보드 제작", "description": "Tableau, Power BI 활용"},
  {"step": 3, "title": "웹 VISUALIZATION", "description": "D3.js, Plotly 활용"},
  {"step": 4, "title": "스토리텔링", "description": "데이터 기반 인사이트 전달"},
  {"step": 5, "title": "인터랙티브 VISUALIZATION", "description": "React 기반 차트 제작"},
  {"step": 6, "title": "PROJECT", "description": "실전 VISUALIZATION PROJECT 수행"}
]', NOW(), NOW());

INSERT INTO curriculum (track, curriculum_steps, created_at, updated_at) VALUES
('ENGINEERING', '[
  {"step": 1, "title": "데이터 ENGINEERING 기초", "description": "SQL, 데이터베이스 기초"},
  {"step": 2, "title": "데이터 파이프라인", "description": "Airflow, ETL 구축"},
  {"step": 3, "title": "클라우드 인프라", "description": "AWS, GCP 기초"},
  {"step": 4, "title": "데이터 웨어하우스", "description": "Redshift, BigQuery 활용"},
  {"step": 5, "title": "실시간 처리", "description": "Kafka, Spark Streaming"},
  {"step": 6, "title": "데이터 품질 관리", "description": "데이터 검증 및 모니터링"},
  {"step": 7, "title": "MLOps 기초", "description": "모델 배포 및 파이프라인 자동화"},
  {"step": 8, "title": "PROJECT", "description": "실전 데이터 파이프라인 구축"}
]', NOW(), NOW());

-- faq 임시데이터 - RECRUITMENT
INSERT INTO faq (question, answer, category, order_num, created_at, updated_at) VALUES
('BOAZ에 RECRUITMENT하려면 어떤 조건이 필요한가요?', '학부생 및 대학원생이라면 누구나 RECRUITMENT 가능합니다. 전공 제한 없이 데이터에 관심 있는 분이라면 환영합니다.', 'RECRUITMENT', 1, NOW(), NOW()),
('RECRUITMENT서는 어디서 작성할 수 있나요?', '공식 홈페이지의 RECRUITMENT하기 버튼을 통해 작성하실 수 있습니다. 모집 기간에만 접수가 가능합니다.', 'RECRUITMENT', 2, NOW(), NOW()),
('비전공자도 RECRUITMENT할 수 있나요?', '네, 가능합니다. 전공보다는 데이터 ANALYSIS에 대한 열정과 학습 의지를 더 중요하게 봅니다.', 'RECRUITMENT', 3, NOW(), NOW()),
('서류 합격 후 면접은 어떤 방식으로 진행되나요?', '면접은 개인 면접으로 진행되며, RECRUITMENT 동기와 간단한 데이터 관련 질문으로 구성됩니다.', 'RECRUITMENT', 4, NOW(), NOW()),
('모집은 1년에 몇 번 진행되나요?', '매 학기 초(3월, 9월)에 신규 기수 모집이 진행됩니다.', 'RECRUITMENT', 5, NOW(), NOW());

-- faq 임시데이터 - ACTIVITY
INSERT INTO faq (question, answer, category, order_num, created_at, updated_at) VALUES
('BOAZ에서는 어떤 ACTIVITY을 하나요?', '스터디, 세미나, PROJECT 등 다양한 ACTIVITY을 진행합니다. 트랙별로 ANALYSIS, VISUALIZATION, ENGINEERING 중 하나를 선택해 심화 학습합니다.', 'ACTIVITY', 1, NOW(), NOW()),
('ACTIVITY 기간은 얼마나 되나요?', '한 기수의 ACTIVITY 기간은 약 6개월(한 학기)입니다.', 'ACTIVITY', 2, NOW(), NOW()),
('PROJECT는 어떤 방식으로 진행되나요?', '팀을 구성해 실제 데이터를 활용한 PROJECT를 수행하며, 학기 말에 발표회를 통해 결과를 공유합니다.', 'ACTIVITY', 3, NOW(), NOW()),
('ACTIVITY비나 RECRUITMENT 혜택이 있나요?', 'ACTIVITY 우수자에게는 소정의 RECRUITMENT금 및 수료증이 제공되며, 외부 대회 참가 시 RECRUITMENT을 받을 수 있습니다.', 'ACTIVITY', 4, NOW(), NOW()),
('세미나는 얼마나 자주 열리나요?', '격주로 정기 세미나가 진행되며, 외부 연사 초청 특강도 비정기적으로 열립니다.', 'ACTIVITY', 5, NOW(), NOW());

-- faq 임시데이터 - ETC
INSERT INTO faq (question, answer, category, order_num, created_at, updated_at) VALUES
('BOAZ SNS 채널이 있나요?', '인스타그램과 링크드인을 운영 중입니다. 공식 홈페이지에서 링크를 확인하실 수 있습니다.', 'ETC', 1, NOW(), NOW()),
('이전 기수의 PROJECT 결과물을 볼 수 있나요?', '네, 공식 홈페이지 및 GitHub에서 이전 기수의 PROJECT 결과물을 확인하실 수 있습니다.', 'ETC', 2, NOW(), NOW()),
('문의는 어디로 하면 되나요?', '공식 이메일 또는 인스타그램 DM으로 문의 주시면 빠르게 답변드리겠습니다.', 'ETC', 3, NOW(), NOW());

-- review 임시데이터 - ANALYSIS
INSERT INTO review (name, track, term, content, image_url, created_at, updated_at) VALUES
('김ANALYSIS', 'ANALYSIS', 15, 'BOAZ에서 데이터 ANALYSIS을 공부하며 정말 많이 성장했습니다. 실전 PROJECT를 통해 이론으로만 알던 머신러닝을 직접 적용해볼 수 있어서 좋았어요.', 'https://example.com/images/review1.jpg', NOW(), NOW()),
('이통계', 'ANALYSIS', 16, '비전공자였지만 BOAZ 덕분에 데이터 ANALYSIS의 기초부터 차근차근 배울 수 있었습니다. 스터디원들과 함께 성장하는 경험이 값졌어요.', 'https://example.com/images/review2.jpg', NOW(), NOW()),
('박파이썬', 'ANALYSIS', 17, 'ANALYSIS 트랙에서 Pandas부터 딥러닝까지 폭넓게 배웠습니다. 캐글 대회에도 참가하며 실력을 쌓을 수 있었던 점이 가장 좋았습니다.', null, NOW(), NOW());

-- review 임시데이터 - VISUALIZATION
INSERT INTO review (name, track, term, content, image_url, created_at, updated_at) VALUES
('최시각', 'VISUALIZATION', 15, 'Tableau와 D3.js를 활용한 VISUALIZATION PROJECT가 정말 인상 깊었습니다. 데이터를 보기 좋게 표현하는 방법을 배운 것이 현업에서도 큰 도움이 되고 있어요.', 'https://example.com/images/review4.jpg', NOW(), NOW()),
('정대시', 'VISUALIZATION', 16, 'VISUALIZATION 트랙에서 단순한 차트를 넘어 스토리텔링까지 배울 수 있었습니다. 데이터로 사람들을 설득하는 방법을 익힌 게 큰 수확이었어요.', 'https://example.com/images/review5.jpg', NOW(), NOW()),
('한인터', 'VISUALIZATION', 17, 'React 기반 인터랙티브 대시보드를 만들어보는 경험이 정말 좋았습니다. 프론트엔드와 데이터를 연결하는 시각을 갖게 되었어요.', null, NOW(), NOW());

-- review 임시데이터 - ENGINEERING
INSERT INTO review (name, track, term, content, image_url, created_at, updated_at) VALUES
('강파이프', 'ENGINEERING', 15, 'Airflow로 데이터 파이프라인을 구축하고 AWS에 배포하는 경험이 정말 값졌습니다. ENGINEERING 트랙을 통해 백엔드와 데이터를 연결하는 시야가 넓어졌어요.', 'https://example.com/images/review7.jpg', NOW(), NOW()),
('윤클라우드', 'ENGINEERING', 16, 'Kafka와 Spark로 실시간 데이터 처리를 경험한 것이 가장 기억에 남습니다. 실제 서비스에 가까운 환경에서 실습할 수 있어서 좋았어요.', 'https://example.com/images/review8.jpg', NOW(), NOW()),
('오엠엘', 'ENGINEERING', 17, 'MLOps 파이프라인을 직접 구축해보며 모델 배포의 전 과정을 이해할 수 있었습니다. 취업 준비에도 큰 도움이 된 경험이었어요.', null, NOW(), NOW());
