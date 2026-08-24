<div align="center">

# BOAZ 공식 홈페이지 — Backend

**국내 최초 빅데이터 동아리 BOAZ 공식 홈페이지의 백엔드 API 서버**
<br />

<img width="1200" height="630" alt="boaz-og" src="https://github.com/user-attachments/assets/55b80705-4850-451f-b7f0-bc3e3ae8592b" />

🔗 Service link: https://www.bigdataboaz.com/

</div>

<br>

## 🛠 Tech Stack

### Backend
- **Language:** Java 21
- **Framework:** Spring Boot 3.4.3
- **Database:** MySQL
- **Database Access:** Spring Data JPA, Hibernate
- **Security:** Spring Security, JWT, OAuth 2.0
- **API Docs:** SpringDoc (Swagger UI)
- **Build Tool:** Gradle

### Infrastructure
- **Cloud (AWS):** EC2, RDS (MySQL), S3, CloudFront, Route53, ACM (SSL), VPC, ALB, CloudWatch
- **CI/CD:** GitHub Actions, AWS CodeDeploy

### Testing
- JUnit 5
- Testcontainers (Docker)

<br>

## 🚀 Getting Started

### Prerequisites
- Java 21
- MySQL 8.x (로컬 실행 시 `boaz` 데이터베이스 필요)

### Installation
```bash
git clone https://github.com/BOAZ-website/backend.git
cd backend
```

### Configuration
> `local` 프로필이 기본으로 활성화됩니다. 아래 "직접 설정 필요"가 ✅인 항목은 값이 없으면 앱이 뜨지 않습니다.

| 환경 변수 | 설명 | 직접 설정 필요 |
|---|---|:---:|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | DB 접속 정보(MySQL 인스턴스 필요)<br>미설정 시 로컬 기본값(`localhost:3306/boaz`, `root`/`0000`) 사용 | |
| `JWT_SECRET` | JWT 서명 시크릿 | ✅ |
| `S3_RECRUITMENT_BUCKET_NAME` / `S3_ARCHIVING_BUCKET_NAME` | S3 버킷명 | ✅ |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth 자격 증명 | ✅ |
| `KAKAO_*` / `NAVER_*` / `AWS_*` | 소셜 로그인·AWS 자격 증명 <br>미설정 시 기본값(placeholder)으로 부팅 | |

### Run
```bash
./gradlew bootRun        # 애플리케이션 실행 (default: local)
./gradlew build          # 빌드
./gradlew test           # 테스트
```

### API Docs
> 실행 후 Swagger UI에서 API 명세를 확인할 수 있습니다.
```
http://localhost:8080/swagger-ui.html
```

<br>

## 📂 Project Structure
> 도메인 중심(Domain-Driven) 패키지 구조를 적용했습니다.
```
com.boaz.backend
├── domain              # 도메인별 모듈 (controller / service / entity / repository / dto)
│   ├── admin
│   ├── archive
│   ├── auth
│   ├── curriculum
│   ├── faq
│   ├── recruitment
│   ├── review
│   └── user
├── global              # 공통 모듈
│   ├── common          # BaseEntity, ApiResponse 등
│   ├── config
│   ├── exception       # CustomException, ErrorCode
│   └── util
└── BackendApplication.java
```

<br>

## 🏗 Architecture

### 평상시
> 평상시 서비스 운영을 위한 인프라 아키텍처입니다.

<img width="2204" height="1250" alt="BOAZ 웹사이트TF팀 인프라-평시" src="https://github.com/user-attachments/assets/ae14069b-1662-4c96-93a3-96ed3d405120" />

### 지원 모집 시 (HA)
> 지원 모집 기간에는 2개의 EC2와 ALB를 적용하여 고가용성을 확보합니다.

<img width="2360" height="1464" alt="BOAZ 웹사이트TF팀 인프라-지원 모집 시" src="https://github.com/user-attachments/assets/e77fabcc-d16a-475b-888c-c0fc7fb4a363" />

<br>

## 📊 ERD
<img width="1861" height="1505" alt="BOAZ WEB TF ERD" src="https://github.com/user-attachments/assets/a27f4fa9-47cd-4ba4-9e25-db4f9c8f3184" />

<br>

## 📦 Deployment
> GitHub Actions와 AWS CodeDeploy를 활용하여 빌드부터 EC2 배포까지의 전 과정을 자동화했습니다.

### CI/CD Pipeline
``` text
Developer ──(Push/Merge)──> GitHub Actions ──(Upload Bundle)──> AWS S3
                                 │                                │
                             (Trigger)                         (Fetch)
                                 ▼                                ▼
                            AWS CodeDeploy ───(Deploy)────> AWS EC2 (systemd)
```
1. GitHub Actions가 테스트 및 빌드를 수행합니다.
2. 빌드 결과물과 배포 스크립트를 ZIP으로 묶어 S3에 업로드합니다.
3. CodeDeploy가 EC2에 배포하고 systemd를 통해 서비스를 실행합니다.

<br>

## 👥 Contributors

### 웹사이트 개발 TF (2026 상반기)
> BOAZ 공식 홈페이지 신규 구축

> 프로젝트 총괄(PM): [@likell1](https://github.com/likell1)

| Profile | Name | GitHub | 기수 · 부문 | Role |
|---------|------|--------|-----------|------|
| <img src="https://github.com/seoyeon83.png" width="50" height="50" /> | 김서연 | [@seoyeon83](https://github.com/seoyeon83) | 25기 엔지니어링 | Lead |
| <img src="https://github.com/jaewonnow.png" width="50" height="50" /> | 신재원 | [@jaewonnow](https://github.com/jaewonnow) | 25기 엔지니어링 | Member |
| <img src="https://github.com/minseo0313.png" width="50" height="50" /> | 남민서 | [@minseo0313](https://github.com/minseo0313) | 26기 엔지니어링 | Member |

### 관리자 페이지 개발 (2026 하반기, 진행 중)
> 관리자 페이지 개발 · 기존 백엔드 코드 품질 개선 · 서비스 운영 지원

> 프로젝트 총괄(PM): [@Daehyun-Bigbread](https://github.com/Daehyun-Bigbread)

| Profile | Name | GitHub | 기수 · 부문 | Role |
|---------|------|--------|-----------|------|
| <img src="https://github.com/seoyeon83.png" width="50" height="50" /> | 김서연 | [@seoyeon83](https://github.com/seoyeon83) | 25기 엔지니어링 | Lead |
| <img src="https://github.com/minseo0313.png" width="50" height="50" /> | 남민서 | [@minseo0313](https://github.com/minseo0313) | 26기 엔지니어링 | Member |
| <img src="https://github.com/wsxchoi.png" width="50" height="50" /> | 최우성 | [@wsxchoi](https://github.com/wsxchoi.png) | 27기 엔지니어링 | Member |
| <img src="https://github.com/galgalrobot.png" width="50" height="50" /> | 권준희 | [@galgalrobot](https://github.com/galgalrobot) | 27기 엔지니어링 | Member |

