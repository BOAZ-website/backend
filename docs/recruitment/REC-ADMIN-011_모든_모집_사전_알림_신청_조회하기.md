# 모든 모집 사전 알림 신청 조회하기

| 항목 | 내용 |
|------|------|
| ID | REC-ADMIN-011 |
| HTTP Method | GET |
| Domain | Recruitment |
| 화면 | Admin |
| 상태 | 완료 |
| 우선순위 | P1 |
| Notion URL | https://www.notion.so/34959289864881659bc5d263b1f82939 |

---

## 📝 개요

어드민 페이지에서 모집 사전 알림 신청 목록 전체를 조회한다.
`subscription` 테이블에 저장된 모든 레코드를 반환한다.

---

## 📐 비즈니스 규칙

- [ ] 모든 사전 알림 신청 데이터를 반환한다.
- [ ] 신청 데이터가 없는 경우 빈 배열을 반환한다. (404 아님)
- [ ] `created_at` 기준 내림차순(최신순)으로 정렬하여 반환한다.

---

## ⚠️ 예외 처리

| 상황 | HTTP 상태 | 에러 코드 | 사용자 메시지 |
|------|-----------|-----------|--------------|
| AccessToken 미포함 | 401 | `TOKEN_NOT_FOUND` | 토큰이 존재하지 않습니다. |
| AccessToken 형식 오류 또는 위변조 | 401 | `INVALID_TOKEN` | 유효하지 않은 토큰입니다. |
| AccessToken 만료 | 401 | `EXPIRED_TOKEN` | 만료된 토큰입니다. |
| 데이터베이스/서버 내부 오류 | 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류가 발생했습니다. |

---

## 🔌 API 스펙

> 협의 상태: ⚪ 미협의

**Request**

```
GET /api/v1/admin/recruitment/subscriptions
```

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
|---------|------|------|------|------|
| Authorization | Header | STRING | ✅ | `Bearer {accessToken}` |

**Request 예시**

```
GET /api/v1/admin/recruitment/subscriptions
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200 OK)**

```json
{
  "status": 200,
  "data": [
    {
      "id": 3,
      "email": "user3@example.com",
      "created_at": "2026-03-15T10:30:00+09:00"
    },
    {
      "id": 2,
      "email": "user2@example.com",
      "created_at": "2026-03-10T09:00:00+09:00"
    },
    {
      "id": 1,
      "email": "user1@example.com",
      "created_at": "2026-03-01T08:00:00+09:00"
    }
  ]
}
```

**Response (200 OK) — 데이터 없는 경우**

```json
{
  "status": 200,
  "data": []
}
```

**협의 포인트**

- [ ] 응답 필드 구성 (id, email, created_at 외 추가 필드 여부)
- [ ] 페이지네이션 적용 여부 (전체 반환 vs 페이징)

---

## 🧠 의사결정 이력

| 날짜 | 고민했던 선택지 | 최종 결정 | 결정 이유 |
|------|---------------|-----------|-----------|
| YYYY-MM-DD | A안 vs B안 | A안 | [이유] |

---

## 🔗 연관 기능

| 기능 ID | 기능명 | 연관 관계 |
|---------|--------|-----------|
| REC-ADMIN-011 | 모든 모집 사전 알림 신청 삭제하기 | 조회 후 전체 삭제 흐름으로 이어짐 |

---

## 📎 비고

- 어드민 전용 API로, 요청 헤더에 유효한 AccessToken(`Authorization: Bearer {token}`)이 포함되어야 한다.
