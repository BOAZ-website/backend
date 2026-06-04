# 지원서 전체 삭제하기

| 항목 | 내용 |
|------|------|
| ID | REC-ADMIN-010 |
| HTTP Method | DELETE |
| Domain | Recruitment |
| 화면 | Admin |
| 상태 | 완료 |
| 우선순위 | P1 |
| Notion URL | https://www.notion.so/34959289864881328370d77469d733d8 |

---

## 📝 개요

어드민 페이지에서 특정 모집 공고에 제출된 지원서 데이터를 전체 삭제한다.
`applicant_answer` 테이블의 답변 데이터를 먼저 삭제한 후, `applicant` 테이블의 지원자 레코드를 삭제한다.

---

## 📐 비즈니스 규칙

- [ ] 삭제는 recruitmentId 기반으로 수행한다.
- [ ] 모집이 현재 진행 중인 경우(모집 기간 내) 삭제를 거부하고 400을 반환한다.
- [ ] 해당 공고에 지원서 데이터가 없는 경우에도 200을 반환한다. (빈 삭제 허용)
- [ ] `applicant_answer` 데이터를 먼저 삭제한 후 `applicant` 데이터를 삭제한다.
- [ ] 존재하지 않는 recruitmentId로 요청 시 404를 반환한다.

---

## ⚠️ 예외 처리

| 상황 | HTTP 상태 | 에러 코드 | 사용자 메시지 |
|------|-----------|-----------|--------------|
| AccessToken 미포함 | 401 | `TOKEN_NOT_FOUND` | 토큰이 존재하지 않습니다. |
| AccessToken 형식 오류 또는 위변조 | 401 | `INVALID_TOKEN` | 유효하지 않은 토큰입니다. |
| AccessToken 만료 | 401 | `EXPIRED_TOKEN` | 만료된 토큰입니다. |
| `recruitmentId` 경로 변수에 숫자가 아닌 값 입력 시 | 400 | `INVALID_PARAMETER_TYPE` | 파라미터 'recruitmentId'의 값이 올바르지 않습니다. |
| 모집이 현재 진행 중인 경우 | 400 | `RECRUITMENT_NOT_CLOSED` | 모집이 진행 중인 공고의 지원서는 삭제할 수 없습니다. |
| 존재하지 않는 공고 ID | 404 | `RECRUITMENT_NOT_FOUND` | 해당 모집 공고를 찾을 수 없습니다. |
| 데이터베이스/서버 내부 오류 | 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류가 발생했습니다. |

---

## 🔌 API 스펙

> 협의 상태: ⚪ 미협의

**Request**

```
DELETE /api/v1/admin/recruitment/{recruitmentId}/applicants
```

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
|---------|------|------|------|------|
| Authorization | Header | STRING | ✅ | `Bearer {accessToken}` |
| recruitmentId | Path | LONG | ✅ | 지원서를 삭제할 모집 공고의 고유 식별자 |

**Request 예시**

```
DELETE /api/v1/admin/recruitment/12/applicants
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200 OK)**

```json
{
  "status": 200
}
```

**Response (400 Bad Request)**

```json
{
  "status": 400,
  "error_code": "RECRUITMENT_NOT_CLOSED",
  "message": "모집이 진행 중인 공고의 지원서는 삭제할 수 없습니다."
}
```

**Response (404 Not Found)**

```json
{
  "status": 404,
  "error_code": "RECRUITMENT_NOT_FOUND",
  "message": "해당 모집 공고를 찾을 수 없습니다."
}
```

**협의 포인트**

- [ ] 삭제 성공 응답을 `200 OK + null data`로 반환할지, `204 No Content`로 반환할지
- [ ] 모집 진행 중 여부 판단 기준 (start_date ~ end_date 범위 기준인지, 별도 상태 필드 기준인지)

---

## 🧠 의사결정 이력

| 날짜 | 고민했던 선택지 | 최종 결정 | 결정 이유 |
|------|---------------|-----------|-----------|
| YYYY-MM-DD | cascade 삭제 vs 순서 보장 삭제 | 순서 보장 삭제 (`applicant_answer` → `applicant`) | FK 제약 조건으로 인해 답변 데이터를 먼저 삭제해야 지원자 레코드 삭제 가능 |
| YYYY-MM-DD | 진행 중 삭제 허용 vs 거부 | 거부 (400 반환) | 모집 중 지원자 데이터가 삭제되면 신뢰성 문제가 발생하므로 명시적으로 차단 |

---

## 🔗 연관 기능

| 기능 ID | 기능명 | 연관 관계 |
|---------|--------|-----------|
| REC-ADMIN-005 | 모집 공고 삭제하기 | 공고 삭제 전 지원서 전체를 먼저 삭제해야 함 |
| REC-ADMIN-008 | 지원서 질문 삭제하기 | 지원서 삭제 후 질문 삭제 가능 (답변 데이터 제거 후) |

---

## 📎 비고

- 어드민 전용 API로, 요청 헤더에 유효한 AccessToken(`Authorization: Bearer {token}`)이 포함되어야 한다.
- 삭제 전 확인 UI(모달 등)는 프론트엔드에서 처리한다.
- `DRAFT`(임시저장) 상태의 지원서도 `SUBMITTED`와 함께 전체 삭제된다. status 무관하게 해당 공고의 모든 지원서를 삭제한다.
- 모집 마감 후 데이터 정리 시, CSV 다운로드(`REC-ADMIN-001`)로 제출 지원서를 먼저 백업한 뒤 이 API를 호출하는 것을 권장한다.
