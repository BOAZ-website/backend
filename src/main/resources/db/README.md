# DB 마이그레이션 (Flyway)

prod DB의 스키마 변경은 `db/migration/`의 Flyway 마이그레이션 파일로 관리한다. 도입 이슈: #234

## 동작 방식

| 프로필 | Flyway | Hibernate `ddl-auto` | 설명 |
|---|---|---|---|
| prod | 켬 | `validate` | 앱이 뜰 때 Flyway가 아직 실행하지 않은 파일을 버전 순서대로 실행한 뒤, Hibernate가 엔티티와 테이블이 맞는지 검사한다. 맞지 않으면 앱이 뜨지 않는다 |
| dev | 켬 | `validate` | prod와 같다. prod 복제 DB를 쓰는 dev 서버를 올리면 prod보다 먼저 배포해 마이그레이션을 확인한다 |
| test | 켬 | `validate` | Testcontainers의 빈 MySQL 8.4에 V1부터 최신 파일까지 실행한 뒤 엔티티와 대조한다. CI가 PR마다 실행한다 |
| local | 끔 | `create` | Hibernate가 테이블을 만들고 `data.sql`로 시드를 넣는다. 마이그레이션 파일은 `./gradlew test`로 확인한다 |

- Flyway는 DB 안의 `flyway_schema_history` 테이블에 실행한 버전을 기록하고, 기록에 없는 파일만 실행한다.
- `V1__init.sql`은 2026-11-09 기준 prod 스키마(prod `mysqldump --no-data` 결과)다. prod에는 실행하지 않고, 첫 배포 때 "V1까지 적용됨"으로 기록만 한다(아래 "첫 prod 배포").
- Spring Boot 3.4.3이 관리하는 Flyway 10.20.1은 MySQL 8.1까지만 공식 지원한다. RDS MySQL 8.4에서는 업그레이드 권고 경고(`Flyway upgrade recommended`)가 나오지만 실행은 막지 않는다.

## 엔티티를 바꿀 때

1. 엔티티를 바꾸는 PR에 마이그레이션 파일을 함께 넣는다.
2. 로컬에서 `./gradlew test`를 실행한다. local 앱은 Hibernate가 테이블을 만들어서, 마이그레이션 파일을 빠뜨려도 잘 뜬다. 빠뜨린 것은 테스트에서 드러난다.
3. PR 템플릿의 마이그레이션 체크 항목을 확인한다.

## 파일 작성 규칙

- **이름:** `V<버전>__<설명>.sql`. 설명은 영어 소문자와 `_`로 쓴다. 예: `V2.2.1__create_member.sql`
- **버전:** 한 배포 안의 도메인은 소수점 버전으로 나눈다(예: `V2.1` admin, `V2.2` member, `V2.3` attendance). 같은 도메인 안에서는 한 단계 더 나눈다(`V2.1.1`, `V2.1.2`).
- **순서:** 새 파일의 버전은 `dev`에 있는 모든 파일보다 높아야 한다. 이미 실행된 최고 버전보다 낮은 파일이 새로 생기면 Flyway가 앱 시작 때 검증에서 실패한다(기본값 `out-of-order: false`). CI는 빈 DB에 순서대로 실행하므로 이 위반을 잡지 못한다. 리뷰에서 확인한다.
- **DDL 한 문장:** 파일 하나에 DDL 한 문장을 원칙으로 한다. MySQL DDL은 문장 단위로만 되돌려지고 여러 문장을 한 트랜잭션으로 묶을 수 없어서, 파일 안 문장이 여러 개면 앞 문장만 적용된 채 실패할 수 있다.
- **고치지 않는 파일:** prod·dev처럼 여러 사람이 쓰는 DB에 한 번이라도 실행된 파일은 고치지 않는다. 변경은 새 파일로 추가한다. Flyway가 체크섬으로 수정 여부를 검사해서, 고치면 그 DB에서 다음 앱 시작이 실패한다. 공유 DB에 실행되기 전 파일은 고쳐도 된다.
- **롤백 호환:** 배포가 실패하면 CodeDeploy가 직전 버전 앱을 다시 배포한다. 직전 버전 앱이 바뀐 스키마에서 동작하는지 확인한다.
  - 스키마: 직전 버전 앱이 지우거나 이름을 바꾼 컬럼을 참조하지 않는가. NOT NULL 컬럼을 추가하면 직전 버전 앱의 INSERT가 실패하지 않는가.
  - 데이터: 새 앱이 직전 버전 앱이 모르는 값(새 enum 값 등)을 저장할 수 있는가. 직전 버전 앱은 `@Enumerated(STRING)` 컬럼에 모르는 값이 있는 행을 읽지 못한다.
- **데이터 백필:** 마이그레이션에 넣지 않는다. 실행 전에 리뷰를 받고, SQL 원문·실행 일시·영향 행 수를 `dev` → `main` 배포 PR 본문에 남긴다.
- **개인정보:** 개인정보가 담긴 SQL이나 CSV는 저장소에 올리지 않는다.

## 배포

1. (dev 서버가 있으면) prod 복제 DB를 쓰는 dev 서버에 먼저 배포해 마이그레이션과 앱 동작을 확인한다.
2. `dev` → `main` 배포 PR을 머지한다. CD가 CodeDeploy로 배포하고, 새 앱이 뜨면서 마이그레이션을 실행한다.
3. 백필이 있으면 배포가 성공한 뒤 실행하고 배포 PR 본문에 기록한다.

- prod 배포는 RDS 유지보수 시간(매주 금요일 05:00~05:30 KST)과 자동 백업 시간(매일 04:00~04:30 KST)을 피한다. 2026-11-09 조회 기준이다.
- 인프라 동결 기간(2027-01)에는 스키마를 적용할 주를 product-infra에 미리 공유한다.
- 배포 전 스냅샷은 따로 만들지 않는다. prod RDS는 자동 백업(보관 7일)이 켜져 있어서, 보관 기간 안의 원하는 시각으로 복원(시점 복원)할 수 있다. 배포 시작 시각은 GitHub Actions CD 실행 기록에서 확인한다. 2026-11-09 조회 기준이다.

### 첫 prod 배포

- `application-prod.yml`의 `baseline-on-migrate: true`(기록 테이블이 없는 기존 DB를 기준 버전으로 기록), `baseline-version: 1` 때문에, prod에서는 V1을 실행하지 않고 "V1까지 적용됨"으로 기록한 뒤 V2 이상을 실행한다.
- 첫 prod 배포가 성공하면 다음 PR에서 이 두 줄을 지운다. 켜 둔 채로 두면, 실수로 다른 DB에 접속했을 때도 조용히 기준 버전이 기록된다. `application-dev.yml`도 dev 서버 첫 배포 뒤 같이 지운다.

## 마이그레이션이 실패했을 때

증상: 배포가 헬스체크에서 실패하고 CodeDeploy가 직전 버전으로 롤백한다. 앱 로그(`/var/log/boaz/application.log`)에 `FlywayMigrateException` 또는 `Migration ... failed`가 남는다.

직전 버전 앱은 보통 다시 뜬다. 실패한 파일은 직전 버전 앱이 모르는 버전이고, Flyway 기본 설정이 모르는 버전의 실패 기록 하나는 경고만 남기고 넘어가기 때문이다. 다만 **다음 배포 전에는 반드시 아래 복구를 해야 한다.** 새 앱은 자기가 아는 버전의 실패 기록을 보면 시작하지 않는다.

1. EC2에 SSH로 접속해 RDS에 접속한다. 접속 정보는 SSM Parameter Store의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`다.
2. 기록을 확인한다.
   ```sql
   SELECT installed_rank, version, description, success, installed_on
   FROM flyway_schema_history ORDER BY installed_rank;
   ```
   `success = 0`인 행이 실패한 파일이다.
3. 실패한 파일의 SQL과 실제 스키마(`SHOW CREATE TABLE <테이블>`)를 비교해, 파일 안 문장 중 무엇이 적용됐는지 확인한다. DDL이 끝난 직후 연결이 끊긴 경우처럼, 스키마는 바뀌었는데 기록이 없을 수도 있다.
4. 적용된 부분이 있으면 되돌릴지 남길지 정한다.
5. 실패 기록을 지운다.
   ```sql
   DELETE FROM flyway_schema_history WHERE success = 0 AND version = '<실패한 버전>';
   ```
   Flyway `repair` 명령은 실패 기록 삭제 외에 체크섬 재정렬도 하므로 쓰지 않는다. 꼭 필요하면 앱과 같은 Flyway 버전(10.20.1)으로, 실패한 배포 커밋의 `src/main/resources/db/migration`을 위치로 지정해 실행한다.
6. SQL을 고쳐 PR로 올리고 다시 배포한다. 실패한 파일은 성공 기록이 없으므로 고쳐도 된다.

위 방법으로 복구할 수 없으면 자동 백업으로 배포 시작 직전 시각에 복원한다. 시점 복원은 기존 DB를 되돌리는 것이 아니라 새 DB 인스턴스를 만드는 작업이라 인프라 담당이 진행한다. 복원 시각 이후에 들어온 데이터(지원서 등)는 사라지므로 그사이 쓰기가 있었는지 먼저 확인한다.
