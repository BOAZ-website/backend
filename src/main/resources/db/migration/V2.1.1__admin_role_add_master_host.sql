-- 권한 개편(#206, #207, #211, #213): admin.role에 MASTER(서비스운영팀장), HOST(그룹 리더) 추가
-- 값을 목록 끝에 붙여서 기존 행을 다시 쓰지 않는다.
-- admin.team_name은 varchar(20)이라 '그룹리더' 추가에 DDL이 필요 없다.
ALTER TABLE `admin`
  MODIFY COLUMN `role` enum('SUPER','TEAM','MASTER','HOST') NOT NULL;
