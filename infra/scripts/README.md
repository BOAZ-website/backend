# BOAZ 시즌 전환 스크립트

모집 시즌(1년 2회, 각 2주) 동안 active-active HA 구조로 전환했다가 평시엔 단일 인스턴스로 복귀하기 위한 자동화 스크립트.

---

## 디렉터리 구성

```
infra/scripts/
├── register-ssm-params.sh   # SSM Parameter Store에 인프라 설정값 등록 (최초 1회)
├── cf_set_origin.py         # CloudFront 단일 origin 도메인/포트 교체 헬퍼
├── season-up.sh             # 모집 시즌 시작 (single EC2 → ALB + 2 EC2)
└── season-down.sh           # 모집 시즌 종료 (ALB + 2 EC2 → single EC2)
```

---

## 아키텍처 변화

### 평시 (스크립트 미실행 기본 상태)

```
사용자 → Route53 → CloudFront → EC2-A (8080) → RDS (single-AZ)
```

- EC2-B: stopped
- ALB: 존재 X
- Target Group `boaz-api-tg`: EC2-A만 등록(상태 `unused`)
- RDS: Multi-AZ OFF

### 시즌 중 (`season-up.sh` 실행 후)

```
사용자 → Route53 → CloudFront → ALB (80) → ┬─ EC2-A (8080)
                                           └─ EC2-B (8080)

                                         RDS (Multi-AZ: primary + standby)
```

- EC2-B: running
- ALB `boaz-alb`: 신규 생성, listener 80 → Target Group 포워딩
- Target Group: EC2-A + EC2-B 모두 healthy
- RDS: Multi-AZ ON (다른 AZ에 standby 동기 복제)

---

## season-up.sh 흐름 (7단계, 약 10~12분)

| Step | 동작 | 소요시간 |
|------|------|----------|
| 0 | SSM에서 인프라 설정 12개 로드 | ~3초 |
| 1 | EC2-B start → running 상태 대기 | ~60초 |
| 2 | CodeDeploy 마지막 성공 배포를 재실행 (EC2-A/B 모두 최신화). `OneAtATime` 방식 | 3~5분 |
| 3 | ALB 생성 → ACTIVE 대기 → HTTP 80 listener 추가 (TG 포워딩) | ~3분 |
| 4 | EC2-B를 Target Group에 register → EC2-A/B 모두 healthy 대기 | ~2분 |
| 5 | CloudFront origin을 EC2-A 직결 → ALB DNS로 교체 (port 8080 → 80) | 트리거 ~10초, 전파 5~15분 |
| 6 | RDS Multi-AZ ON 트리거 (apply-immediately) | 트리거 ~5초, 실제 완료 10~30분 |
| 7 | 헬스체크 안내 출력 | 즉시 |

### Step 2의 잠재적 다운타임

CodeDeploy `OneAtATime`이 EC2-A를 재배포하는 동안 ~30초 5xx 발생 가능. 시즌-up은 **트래픽 적은 시간대(예: 새벽)**에 실행 권장.

---

## season-down.sh 흐름 (5단계, 약 6~20분)

| Step | 동작 | 소요시간 |
|------|------|----------|
| 0 | SSM에서 인프라 설정 로드 | ~3초 |
| 1 | CloudFront origin을 ALB → EC2-A 직결로 복귀 (port 80 → 8080) | 트리거 ~10초 |
| 2 | CloudFront `Deployed` 상태 폴링 (전파 완료 대기) | 5~15분 |
| 3 | ALB 삭제 (listener 함께 삭제) | ~10초 |
| 4 | EC2-B를 Target Group에서 deregister + stop | ~30초 |
| 5 | RDS Multi-AZ OFF 트리거 (apply-immediately) | 트리거 ~5초, 실제 완료 10~30분 |

### Step 2를 왜 폴링하는가

CloudFront 전파가 끝나기 전에 ALB를 삭제하면, 아직 ALB origin을 사용하는 edge 캐시들이 502를 반환한다. 안전하게 `Deployed` 상태가 된 후에 ALB를 삭제.

---

## 영구 유지되는 인프라 (삭제 금지)

스크립트가 매 시즌 재사용하므로 절대 손대지 말 것.

- **Target Group** `boaz-api-tg` (EC2-A 영구 등록)
- **ALB Security Group** `boaz-alb-sg` (CloudFront prefix list만 허용)
- **EC2 SG의 ALB SG 인바운드 룰** (8080 허용)
- **SSM Parameter Store** `/boaz/infra/*` 12개 파라미터
- **EC2-B 인스턴스** (stopped 상태로 유지, ID 보존)

---

## 시즌마다 생성/삭제되는 인프라

- **ALB** `boaz-alb` (DNS는 매번 달라짐, 스크립트가 CloudFront에 자동 반영)
- **ALB Listener** (ALB와 함께 생성/삭제)
- **EC2-B의 Target Group 등록 상태**
- **RDS Multi-AZ 토글**

---

## 사전 준비 (최초 1회)

### 1. AWS 계정 / CLI 설정

- AWS CLI 설치 및 `tf` 프로파일 구성 (`aws configure --profile tf`)
- 권한: EC2/ELBv2/CloudFront/RDS/CodeDeploy/SSM 관리 권한 필요

### 2. Python 환경 (Windows)

`cf_set_origin.py`가 표준 라이브러리만 쓰므로 별도 패키지 불필요. 다만 Windows Git Bash 환경에서 `python` 명령이 WindowsApps 스텁으로 가는 것을 피하기 위해 conda 가상환경 권장.

```bash
conda create -n boaz-ops python=3.11 -y
```

이후 매번 스크립트 실행 전:

```bash
conda activate boaz-ops
```

> conda init bash가 안 되어 있으면 Anaconda Prompt에서 `conda init bash` 한 번 실행 후 Git Bash 재시작.

### 3. SSM 파라미터 등록

```bash
cd infra/scripts
./register-ssm-params.sh
```

값이 바뀌었으면 `register-ssm-params.sh` 안의 `put-parameter` 호출에 `--overwrite` 옵션을 추가하고 재실행.

### 4. 영구 인프라 세팅 (한 번만 콘솔에서)

- Target Group `boaz-api-tg` 생성 (포트 8080, 헬스체크 `/actuator/health`, EC2-A 등록)
- ALB Security Group `boaz-alb-sg` 생성 (inbound: CloudFront prefix list만 허용)
- EC2-A/B의 Security Group에 `boaz-alb-sg → 8080` 허용 룰 추가

---

## 실행 가이드

### 시즌 시작 (season-up)

```bash
conda activate boaz-ops
cd infra/scripts
./season-up.sh
```

#### 실행 후 검증

```powershell
# 1. ALB 생성 확인
aws elbv2 describe-load-balancers --names boaz-alb --region ap-northeast-2 --profile tf \
  --query "LoadBalancers[0].{State:State.Code,DNS:DNSName}" --output table

# 2. Target Group에 EC2-A, EC2-B 모두 healthy
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:ap-northeast-2:156312218841:targetgroup/boaz-api-tg/d42747d4f4ff49e6 \
  --region ap-northeast-2 --profile tf \
  --query "TargetHealthDescriptions[].{Id:Target.Id,Health:TargetHealth.State}" --output table

# 3. CloudFront origin이 ALB DNS인지
aws cloudfront get-distribution-config --id E2SER81QYNPRO9 --profile tf \
  --query "DistributionConfig.Origins.Items[].{Domain:DomainName,Port:CustomOriginConfig.HTTPPort}" --output table

# 4. RDS Multi-AZ 전환 상태
aws rds describe-db-instances --db-instance-identifier boaz-prod-db --region ap-northeast-2 --profile tf \
  --query "DBInstances[0].{Status:DBInstanceStatus,MultiAZ:MultiAZ,Pending:PendingModifiedValues.MultiAZ}" --output table

# 5. 전파 완료 후 실제 호출 (5~15분 후)
curl https://api.bigdataboaz.com/actuator/health
```

---

### 시즌 종료 (season-down)

```bash
conda activate boaz-ops
cd infra/scripts
./season-down.sh
```

#### 실행 후 검증

```powershell
# 1. CloudFront origin이 EC2-A로 복귀
aws cloudfront get-distribution-config --id E2SER81QYNPRO9 --profile tf \
  --query "DistributionConfig.Origins.Items[].{Domain:DomainName,Port:CustomOriginConfig.HTTPPort}" --output table

# 2. ALB 삭제 확인 (LoadBalancerNotFound 에러가 나야 정상)
aws elbv2 describe-load-balancers --names boaz-alb --region ap-northeast-2 --profile tf

# 3. EC2-B stopped
aws ec2 describe-instances --instance-ids i-05405847d3897364a --region ap-northeast-2 --profile tf \
  --query "Reservations[0].Instances[0].State.Name" --output text

# 4. Target Group에 EC2-A만 남음
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:ap-northeast-2:156312218841:targetgroup/boaz-api-tg/d42747d4f4ff49e6 \
  --region ap-northeast-2 --profile tf \
  --query "TargetHealthDescriptions[].{Id:Target.Id,Health:TargetHealth.State}" --output table

# 5. 실제 호출
curl https://api.bigdataboaz.com/actuator/health
```

---

## 실행 환경 주의사항

### Windows Git Bash

- 경로 자동 변환을 막기 위해 스크립트 내부에서 `MSYS_NO_PATHCONV=1` 자동 설정
- 직접 AWS CLI 호출 시(예: SSM 등록을 PowerShell이 아닌 Git Bash로 할 때) 동일 환경변수 필요
- Python은 conda 가상환경(`boaz-ops`) 활성화 상태여야 함

### AWS Profile

기본값은 `tf`. 다른 프로파일 쓰려면:

```bash
AWS_PROFILE=other ./season-up.sh
```

---

## 문제 발생 시 수동 롤백

### season-up 도중 실패한 경우

1. ALB 생성됐으면 콘솔에서 삭제
2. EC2-B를 Target Group에서 deregister
3. EC2-B stop
4. CloudFront origin이 EC2-A인지 확인, 아니면 콘솔에서 수동 복귀(domain + port 8080)
5. RDS Multi-AZ 토글된 상태면 콘솔에서 되돌리기

### season-down 도중 실패한 경우

1. CloudFront origin 상태 확인 후 필요시 수동 복귀
2. ALB 남아있으면 콘솔에서 삭제
3. EC2-B 상태 확인
4. RDS Multi-AZ 토글된 상태면 콘솔에서 되돌리기

---

## 값 관리 정책

스크립트가 사용하는 값은 두 종류로 나뉜다.
- **고정값**: 인프라 리소스 식별자/설정. SSM Parameter Store에 저장하고 스크립트가 시작 시 한 번 로드한다. 값이 바뀌는 시점이 매우 드물다.
- **동적값**: 스크립트 실행 도중 AWS API 호출로 조회/생성된다. 매 실행마다 값이 달라지므로 저장하지 않는다.

### 고정값 (SSM Parameter Store에 저장)

경로: `/boaz/infra/*` (12개). `register-ssm-params.sh`로 등록/갱신.

| SSM 키 | 현재 값 | 용도 / 사용 스크립트 | 변경 시점 |
|--------|---------|---------------------|-----------|
| `EC2_B_ID` | `i-05405847d3897364a` | EC2-B start/stop, TG 등록·해제 (up/down) | EC2-B terminate 후 재생성 시 |
| `EC2_A_DOMAIN` | `ec2-15-165-102-5.ap-northeast-2.compute.amazonaws.com` | CloudFront origin 복귀 (down) | EC2-A 퍼블릭 DNS 변경 시 (EIP 권장) |
| `EC2_A_PORT` | `8080` | CloudFront origin 복귀 포트 (down) | Spring Boot 포트 변경 시 |
| `CLOUDFRONT_DIST_ID` | `E2SER81QYNPRO9` | CloudFront origin 업데이트 (up/down) | CloudFront 재생성 시 |
| `RDS_IDENTIFIER` | `boaz-prod-db` | Multi-AZ 토글 (up/down) | RDS 재생성 시 |
| `S3_BUCKET` | `boaz-codedeploy-bucket` | CodeDeploy 번들 위치 참조 | 거의 안 바뀜 |
| `CODEDEPLOY_APP` | `boaz-backend` | CodeDeploy 배포 (up) | 거의 안 바뀜 |
| `CODEDEPLOY_GROUP` | `codedeploy-prod` | CodeDeploy 배포 (up) | 거의 안 바뀜 |
| `TARGET_GROUP_ARN` | `arn:aws:elasticloadbalancing:ap-northeast-2:156312218841:targetgroup/boaz-api-tg/d42747d4f4ff49e6` | ALB listener forwarding, EC2-B 등록·해제 (up/down) | Target Group 재생성 시 |
| `ALB_SG_ID` | `sg-0b98cad292282ebe5` | ALB 생성 시 SG 지정 (up) | ALB SG 재생성 시 |
| `ALB_SUBNETS` | `subnet-02fd34391da42563b,subnet-092734d1357224a25` | ALB 생성 시 배치 서브넷 (up) | VPC 서브넷 재구성 시 |
| `ALB_PORT` | `80` | ALB listener 포트 + CloudFront origin 포트 (up) | ALB 표준 포트 변경 시(거의 없음) |

### 동적값 (스크립트가 런타임에 조회)

저장하지 않고 매 실행마다 새로 가져온다. 저장하면 stale 데이터가 되어 사고로 이어지므로 의도적으로 저장 안 함.

| 변수 | 어떻게 얻는가 | 왜 동적인가 |
|------|--------------|-------------|
| `ALB_ARN` | `elbv2 create-load-balancer` 응답 | 시즌마다 ALB를 새로 생성하므로 매번 다른 ARN |
| `ALB_DNS` | `elbv2 describe-load-balancers --load-balancer-arns $ALB_ARN` | ALB 생성 시점에 AWS가 부여하는 도메인 (예: `boaz-alb-XXXXXX.ap-northeast-2.elb.amazonaws.com`). 매번 다른 prefix |
| `LAST_DEPLOY_ID` | `deploy list-deployments --include-only-statuses Succeeded` | 마지막 성공 배포가 매번 다름 (코드 변경마다 새 배포 ID 발급) |
| `BUNDLE_BUCKET`, `BUNDLE_KEY` | `deploy get-deployment $LAST_DEPLOY_ID` | 배포마다 새 S3 키로 번들 업로드됨 |
| `DEPLOYMENT_ID` | `deploy create-deployment` 응답 | 신규 배포 트리거할 때마다 새로 발급. `deploy wait deployment-successful`에 필요 |
| `ETAG` | `cloudfront get-distribution-config --query ETag` | CloudFront 설정이 바뀔 때마다 새 ETag 발급. `update-distribution --if-match`에 필수 (낙관적 락) |
| `DIST_CONFIG` | `cloudfront get-distribution-config --query DistributionConfig` | 현재 distribution 전체 설정. origin만 수정해서 다시 PUT 해야 하므로 매번 fresh fetch 필요 |

#### 동적값 흐름 예시 (season-up Step 5: CloudFront origin 교체)

```
1. ETAG = get-distribution-config로 현재 ETag 조회
2. DIST_CONFIG = 같은 호출로 현재 전체 설정 조회
3. cf_set_origin.py로 origin의 DomainName/HTTPPort만 ALB 값으로 치환 → NEW_CONFIG
4. update-distribution --if-match $ETAG --distribution-config $NEW_CONFIG
   → ETAG가 안 맞으면 누군가 그 사이 설정 바꾼 것 → 에러로 실패 (안전장치)
```
