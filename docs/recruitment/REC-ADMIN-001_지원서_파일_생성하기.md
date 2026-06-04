# 지원서 파일 생성하기

| 항목 | 내용 |
|------|------|
| ID | REC-ADMIN-001 |
| HTTP Method | POST |
| Domain | Recruitment |
| 화면 | Admin |
| 상태 | 완료 |
| 우선순위 | P0 |
| Notion URL | https://www.notion.so/36359289864881a1af25f1d299b9a743 |

---

## 📝 개요

특정 모집 공고에 제출된 모든 지원자의 지원서를 하나의 CSV 파일로 다운로드한다.
이때 모든 지원서를 가져오거나, 부문별 지원서만 가져올 수도 있다.

---

## 💾 데이터 구조 (ERD)

---

## 📐 비즈니스 규칙

- [ ] API 호출 시, 각 부문 별로 CSV 파일을 하나씩 생성한다 (총 3개)
- [ ] `SUBMITTED` 상태의 지원서만 추출한다. `DRAFT`(임시저장) 상태는 포함하지 않는다.
- [ ] 각 지원자를 하나의 행으로, 기본 정보와 질문을 별도의 열로 배치한다.
- [ ] 컬럼은 기본 정보 + 공통 질문 답변 + 부문별 질문 답변 순서로 구성한다. 기본 정보 컬럼 순서는 다음과 같다: `user_id`, 지원 부문, 성명, 이메일 주소, 전화번호, 대학교, 본전공, 복수/부전공, 마지막 재학 학기, 병역 이수 여부, 생년월일, 졸업 예정 시점, 대학원 진학 여부, 지원서 제출 일시
- [ ] TABLE 타입의 답변(JSON)은 한 셀 내에서 `키: 값` 형태의 문자열로 표기한다. 복수선택(`multiple: true`) 답변의 경우 값이 배열이므로 `키: 값1, 값2` 형태로 join하여 표기한다. (예: `9월 7일(일): 12:00~14:00, 16:00~18:00`)
- [ ] CSV 형태로 파일을 생성하며, UTF-8 with BOM 인코딩을 적용한다.
- [ ] 생성된 파일은 private S3 버킷에 저장된다. 경로: `s3://boaz-recruitment/{term}/applicants_{track}_{timestamp}.csv`
- [ ] 제출 순서(`submitted_at`)로 데이터를 오름차순 정렬한다.

---

## ⚠️ 예외 처리

| 상황 | HTTP 상태 | 에러 코드 | 사용자 메시지 |
|------|-----------|-----------|--------------|
| 지원 데이터가 없는 경우 | 200 | - | 헤더(컬럼명)만 포함된 파일 다운로드 |
| AccessToken 미포함 | 401 | `TOKEN_NOT_FOUND` | 토큰이 존재하지 않습니다. |
| AccessToken 형식 오류 또는 위변조 | 401 | `INVALID_TOKEN` | 유효하지 않은 토큰입니다. |
| AccessToken 만료 | 401 | `EXPIRED_TOKEN` | 만료된 토큰입니다. |
| Admin 권한 없음 | 403 | `ACCESS_DENIED` | 해당 리소스에 접근할 권한이 없습니다. |
| 존재하지 않는 공고 ID | 404 | `RECRUITMENT_NOT_FOUND` | 해당 모집 공고를 찾을 수 없습니다. |
| S3 업로드 실패 | 500 | `S3_UPLOAD_FAILED` | 파일 업로드 중 오류가 발생했습니다. |
| 서버 내부 오류 | 500 | `INTERNAL_SERVER_ERROR` | 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요. |

---

## 🔌 API 스펙

> 협의 상태: ✅ 확정

**Request**

```
POST /api/v1/admin/recruitment/applications/download
```

| 파라미터명 | 위치 | 타입 | 필수 | 설명 |
|---------|------|------|------|------|
| Authorization | Header | STRING | ✅ | `Bearer {accessToken}` |
| term | Query | INT | ✅ | 모집 공고 기수 |

**Request 예시**

```
POST /api/v1/admin/recruitment/applications/download?term=27
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200 OK)**

```json
{
  "status": 200
}
```

S3 저장 경로: `s3://boaz-recruitment/{term}/applicants_{track}_{timestamp}.csv`

**Response (404 Not Found)**

```json
{
  "status": 404,
  "error_code": "RECRUITMENT_NOT_FOUND",
  "message": "해당 모집 공고를 찾을 수 없습니다."
}
```

**협의 포인트**

- [x] `recruitment_id`로 조회할 건지? (DB 내부 사정을 모르면 굳이니까) ⇒ `term`으로 조회

---

## 🧠 의사결정 이력

| 날짜 | 고민했던 선택지 | 최종 결정 | 결정 이유 |
|------|---------------|-----------|-----------|
| 2026-02-22 | 전체/부문 API 분리 vs 통합 | 통합 | 결국 CSV 파일을 추출해 저장한다는 것은 같은 로직이기 때문에 통합 |
| 2026-02-22 | 동적 컬럼 vs 고정 컬럼 | 동적 컬럼 | 사용자 경험의 편의성을 우선 |

---

## 🔗 연관 기능

| 기능 ID | 기능명 | 연관 관계 |
|---------|--------|-----------|
| REC-005 | 지원서 제출하기 | 본 기능에서 추출할 원천 데이터가 생성되는 기능 (REC-004 대체) |

---

## 📎 비고

- 비동기 방식 고려 필요 (대량 DB read, file I/O 때문에)
- 개인정보가 포함되어 있으므로 꼼꼼한 보안 정책이 필요함
