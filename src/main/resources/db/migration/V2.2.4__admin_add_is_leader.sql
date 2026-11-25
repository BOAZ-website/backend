-- 시스템 계정(#223): 운영진 팀장 여부. 표시와 점수 계산에만 쓰고 권한 계산에는 쓰지 않는다.
-- DB 기본값 FALSE라서 직전 버전 앱(이 컬럼을 모르는 엔티티)의 INSERT도 실패하지 않는다.
-- 기존 계정 값(MASTER는 true, HOST는 false, 나머지는 팀장 명단)은 릴리즈 때 백필한다.
ALTER TABLE `admin`
  ADD COLUMN `is_leader` boolean NOT NULL DEFAULT FALSE;
