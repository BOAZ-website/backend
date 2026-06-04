# 모든 모집 사전 알림 신청 삭제하기

| 항목 | 내용 |
|------|------|
| ID | REC-ADMIN-012 |
| HTTP Method | DELETE |
| Domain | Recruitment |
| 화면 | Admin |
| 상태 | 완료 |
| 우선순위 | P1 |
| Notion URL | https://www.notion.so/34959289864881efbd82ee596944a711 |

---

## 📝 개요

어드민 페이지에서 모집 사전 알림 신청 데이터를 전체 삭제한다.
`subscription` 테이블의 모든 레코드를 삭제한다.

---

## 📐 비즈니스 규칙

- [ ] `subscription` 테이블의 모든 레코드를 삭제한다.
- [ ] 삭제할 데이터가 없는 경우(0건)에도 200을 반환한다.

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
DELETE /api/v1/admin/recruitment/subscriptions
```

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
|---------|------|------|------|------|
| Authorization | Header | STRING | ✅ | `Bearer {accessToken}` |

**Request 예시**

```
DELETE /api/v1/admin/recruitment/subscriptions
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200 OK)**

```json
{
  "status": 200
}
```

**협의 포인트**

- [ ] 삭제 성공 응답을 `200 OK + null data`로 반환할지, `204 No Content`로 반환할지
- [ ] 삭제된 건수를 응답에 포함할지 여부

---

## 🧠 의사결정 이력

| 날짜 | 고민했던 선택지 | 최종 결정 | 결정 이유 |
|------|---------------|-----------|-----------|
| YYYY-MM-DD | A안 vs B안 | A안 | [이유] |

---

## 🔗 연관 기능

| 기능 ID | 기능명 | 연관 관계 |
|---------|--------|-----------|
| REC-ADMIN-010 | 모든 모집 사전 알림 신청 조회하기 | 조회 후 전체 삭제 흐름으로 이어짐 |

---

## 📎 비고

- 어드민 전용 API로, 요청 헤더에 유효한 AccessToken(`Authorization: Bearer {token}`)이 포함되어야 한다.
- 삭제 전 확인 UI(모달 등)는 프론트엔드에서 처리한다.
