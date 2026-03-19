# backend

# 🌿 Git Branch 전략 및 커밋 컨벤션

## 📌 브랜치 종류 및 규칙

| 브랜치 | 용도 | 설명 |
|--------|------|------|
| `main` | 배포용 | 항상 **안정적인 상태** 유지. 배포 시 이 브랜치 기준으로 진행. **직접 작업 금지.** |
| `dev` | 개발 통합 | 각 기능 브랜치를 이 브랜치로 merge. 팀원 PR 후 코드리뷰 → merge 권장. 리뷰 지연 시 **자기 책임 하에 직접 merge 가능.** |
| `feat/{이슈번호}-{설명}` | 기능 개발 | 새로운 기능 개발 시 사용. 예: `feature/#12-login-api` |
| `fix/{이슈번호}-{설명}` | 버그 수정 | 발견된 버그 수정용 브랜치. |
| `hotfix/{이슈번호}-{설명}` | 긴급 수정 | 배포 후 발생한 긴급 이슈 처리 시 사용. |
| `refactor/{이슈번호}-{설명}` | 리팩토링 | 로직 변경 없이 코드 구조 개선 목적. |
| `chore/{이슈번호}-{설명}` | 설정/환경 | 빌드 설정, 패키지 설치 등 부수 작업 시 사용. |

> ✅ 기능 개발이 완료되면 `develop` 브랜치로 **Pull Request → Merge**  
> ✅ merge 완료된 브랜치는 **즉시 삭제 권장**

---

## ✅ 브랜치 네이밍 예시

- `feat/#12-login-api`
- `fix/#17-cors-error`
- `chore/#20-env-setting`

---

## 📝 커밋 메시지 컨벤션

### 🔧 커밋 메시지 형식

```
#이슈명 타입: 변경 요약
```

> 예시:  
> `#1 feat: 로그인 API 구현`

---

### 📚 커밋 타입 정의

| 태그 | 설명 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `hotfix` | 급한 버그/이슈 패치 |
| `refactor` | 코드 리팩토링 (기능 변화 없음) |
| `add` | 부가적인 코드/라이브러리/파일 추가 |
| `del` | 불필요한 코드/파일 삭제 |
| `docs` | 문서 작업 (README, Wiki 등) |
| `chore` | 환경 설정, 빌드 작업 등 기타 잡일 |
| `correct` | 오타, 타입 수정 등 |
| `move` | 코드/파일 위치 이동 |
| `rename` | 파일/변수/함수 이름 변경 |
| `improve` | 성능/UX 개선 |
| `test` | 테스트 코드 작성/수정 |

---

## 🔁 브랜치 Workflow 요약

1. 이슈 생성  
2. `develop` 브랜치 기준으로 기능 브랜치 생성  
   ```bash
   git checkout -b feature/#12-login-api develop
   ```

3. 기능 개발 후 커밋 & 푸시  
   ```bash
   git commit -m "#12 feat: 로그인 API 구현"
   git push origin feature/#12-login-api
   ```

4. GitHub에서 Pull Request 생성  
5. 코드리뷰 후 merge (지연 시 셀프 merge 가능)  
6. merge된 브랜치는 삭제  

---
