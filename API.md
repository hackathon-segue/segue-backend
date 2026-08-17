# API.md — Segue Backend API 명세

Base URL (로컬): `http://localhost:8080`
모든 요청/응답은 `application/json`. CORS는 `/api/**` 전체에 대해 모든 origin 허용(`CorsConfig`).

에러 응답 공통 포맷:
```json
{ "message": "사람이 읽을 수 있는 에러 설명" }
```
- 404: 리소스 없음 (예: 고객/SKU 조회 실패)
- 400: 요청 값 검증 실패
- 403: 고객 동의가 필요함 (기능명세서 5번, 아래 "동의 관리" 참고)
- 502: AI(OpenAI) 호출 실패

---

## 전체 플로우 순서 (프론트 구현 가이드)

1. **고객 모바일**: `POST /api/cart` 로 컬러/사이즈 선택 후 담기
2. **태블릿**: `GET /api/customers/lookup` 로 고객 조회
3. 응답의 `hasConsented` 가 `false` 면 데이터 이용 동의 화면을 먼저 보여주고 `POST /api/customers/{id}/consent` 로 기록
4. `GET /api/cart` 로 장바구니+재고 확인 (동의 안 된 고객이면 403)
5. 품절 SKU 하나 선택 → `POST /api/consultations/intent` 로 발화 구조화
6. `needsFollowUp=true` 면 `POST /api/consultations/followup-question` → 고객 답변 받기 → `POST /api/consultations/followup-answer`
7. CA/고객이 구조화된 조건을 화면에서 확인·수정 (프론트 로컬 상태 편집, 별도 API 없음)
8. `POST /api/consultations/decide` 호출 (이 호출 동안 프론트는 "AI 분석 중" 로딩 상태만 표시, 별도 화면 전환 없음)
9. Last Intent Card 표시 → 실행 버튼 탭 → `POST /api/consultations/execute`
10. 완료 메시지 표시. 이후 CA가 실제 확인 결과를 알게 되면 `PATCH /api/consultations/{id}/execution-status` 로 후속 상태 갱신
11. **고객 모바일**: `GET /api/consultations/customers/{customerId}?page=0&size=10` 으로 결과 및 현재 처리 상태 확인

> **무상태 설계**: 서버는 상담 진행 중 상태를 세션으로 들고 있지 않는다. 5-9 단계에서 서버가 응답한 값(특히 `structuredIntent`, `decide` 응답 전체)은 프론트가 들고 있다가 다음 요청에 그대로 담아 보내야 한다.

---

## 1. 고객 조회 (F1)

### `GET /api/customers/lookup?phoneNumber={phoneNumber}`

응답 `200`:
```json
{ "id": 1, "name": "김세계", "phoneNumber": "010-1234-5678", "hasConsented": true }
```
`hasConsented=false` 면 장바구니 조회 전에 동의부터 받아야 한다 (아래 "동의 관리" 참고).

응답 `404`: 일치하는 고객 없음 → "회원 정보를 다시 확인해 주세요" 문구로 안내.

테스트 계정: `010-1234-5678`(김세계, 이미 동의됨), `010-9876-5432`(이수현, 동의 기록 없음 — 403 데모용)

---

## 1-1. 고객 동의 관리 (기능명세서 5번)

장바구니 조회, 상담 결과 저장, 고객 모바일 재확인은 **고객이 동의한 경우에만** 허용된다. 동의하지 않으면 해당 API들은 `403`을 반환한다.

### `POST /api/customers/{customerId}/consent`

CA가 고객에게 데이터 이용 목적·범위를 안내한 뒤 동의/비동의 의사를 기록한다. 반복 제출 시 마지막 의사로 덮어쓴다.

요청:
```json
{ "agreed": true }
```
응답 `200`:
```json
{
  "customerId": 1,
  "status": "AGREE",
  "scope": "장바구니 조회, 구매 의도·상담 결과 저장, 고객 모바일 재확인",
  "consentedAt": "2026-08-16T17:30:41"
}
```

### `GET /api/customers/{customerId}/consent`

현재 동의 상태 조회. 아직 한 번도 응답하지 않았으면 `404`.

### 동의 필요 시 에러 (예: `GET /api/cart`, `POST /api/consultations/execute`, `GET /api/consultations/customers/{id}`)

응답 `403`:
```json
{ "message": "고객 동의가 필요합니다. 장바구니 조회·상담 결과 저장 전에 데이터 이용 동의를 먼저 받아 주세요." }
```
프론트는 이 응답을 받으면 동의 화면으로 안내하고, 동의 처리 후 원래 요청을 재시도한다.

---

## 2. 장바구니 저장 (F0)

### `POST /api/cart`

요청:
```json
{ "customerId": 1, "productId": 1, "color": "블랙", "size": "미디움" }
```
- `productId` + `color` + `size` 조합으로 서버가 SKU를 찾아 저장한다 (일치하는 SKU가 없으면 404).

응답 `201`:
```json
{
  "cartItemId": 10,
  "productId": 1,
  "productName": "MCM 백팩 미디움",
  "imageUrl": "https://...",
  "category": "백팩",
  "skuId": 1,
  "color": "블랙",
  "size": "미디움",
  "currentStoreInStock": false,
  "otherStoreInStock": false,
  "restockPlanned": false,
  "actionButtonLabel": "Last Intent 시작",
  "savedAt": "2026-08-16T15:00:00"
}
```
> 저장 시점에는 `storeId` 문맥이 없으므로 재고 필드는 전부 `false`로 내려간다. 실제 재고 상태는 아래 F2 조회에서 `storeId`를 넘겨 확인한다.

---

## 3. 장바구니 + 재고 확인 (F2)

### `GET /api/cart?customerId={customerId}&storeId={storeId}`

- `storeId`는 선택이지만, 태블릿에서는 반드시 현재 매장 ID를 넘겨야 정확한 재고 상태가 나온다.
- 응답은 최근 담은 순(`savedAt` DESC).

응답 `200`:
```json
[
  {
    "cartItemId": 1, "productId": 1, "productName": "MCM 백팩 미디움",
    "imageUrl": "https://...", "category": "백팩",
    "skuId": 1, "color": "블랙", "size": "미디움",
    "currentStoreInStock": false, "otherStoreInStock": true, "restockPlanned": false,
    "actionButtonLabel": "Last Intent 시작",
    "savedAt": "2026-08-16T16:54:29"
  },
  {
    "cartItemId": 3, "productId": 4, "productName": "MCM 숄더백 미니",
    "imageUrl": "https://...", "category": "숄더백",
    "skuId": 4, "color": "베이지", "size": "미니",
    "currentStoreInStock": true, "otherStoreInStock": true, "restockPlanned": false,
    "actionButtonLabel": "제품 확인하기",
    "savedAt": "2026-08-16T16:19:29"
  }
]
```
- `actionButtonLabel` 이 `"Last Intent 시작"` 인 항목이 여러 개면, 프론트는 전부 목록으로 노출하고 **하나씩 순서대로** Last Intent 플로우(아래 4-8)를 진행한다. 한 SKU가 완료되면 목록에서 다음 SKU로 넘어가거나 CA가 수동 선택.

---

## 4. 고객 의도 구조화 (F3)

### `POST /api/consultations/intent`

요청:
```json
{ "storeId": 1, "skuId": 1, "utterance": "이 로고 위치와 각진 형태가 좋아요. 오늘 살 필요는 없어요" }
```

응답 `200`:
```json
{
  "structuredIntent": {
    "purpose": "",
    "essentialConditions": { "logoPosition": "정면중앙", "silhouette": "각진" },
    "preferredConditions": {},
    "negotiableConditions": {},
    "purchaseUrgency": "FLEXIBLE",
    "physicalCheckAttributes": [],
    "canWait": true,
    "canVisitOtherStore": true,
    "needsFollowUp": false,
    "followUpReason": ""
  },
  "needsFollowUp": false
}
```

### `StructuredIntentDto` 필드 설명 (이후 모든 단계에서 동일한 구조 사용)

| 필드 | 타입 | 설명 |
|---|---|---|
| purpose | string | 사용 목적 (자유 텍스트) |
| essentialConditions | object(string→string) | 필수 조건. key는 아래 "속성 어휘" 표의 key만 가능 |
| preferredConditions | object(string→string) | 선호 조건 |
| negotiableConditions | object(string→string) | 양보 가능 조건 |
| purchaseUrgency | `"TODAY"` \| `"THIS_WEEK"` \| `"FLEXIBLE"` | 구매 시급성 |
| physicalCheckAttributes | string[] | 실물로 확인하고 싶은 속성 key 목록 |
| canWait | boolean \| null | 대기 가능 여부 |
| canVisitOtherStore | boolean \| null | 타 매장 방문 가능 여부 |
| needsFollowUp | boolean | 보충 질문 필요 여부 |
| followUpReason | string | 보충 질문이 필요한 이유(내부 참고용, 화면 노출 불필요) |

### 속성 어휘 (essential/preferred/negotiable/physicalCheckAttributes 의 key, value 값)

| key | 가능한 value |
|---|---|
| colorFamily | 블랙 \| 브라운 \| 베이지 |
| colorTone | 웜 \| 쿨 \| 뉴트럴 |
| material | 가죽 \| 캔버스 \| 패브릭 |
| glossLevel | 높음 \| 중간 \| 낮음 |
| logoVisibility | 높음 \| 중간 \| 낮음 |
| logoPosition | 정면중앙 \| 정면하단 \| 스트랩 |
| patternDensity | 높음 \| 중간 \| 낮음 |
| silhouette | 각진 \| 라운드 \| 사각 |
| structure | 하드 \| 소프트 |
| sizeGrade | 미니 \| 스몰 \| 미디움 \| 라지 |
| strapType | 체인스트랩 \| 패브릭스트랩 \| 패브릭+레더콤보 \| 벨트스트랩 |
| hardwareColor | 골드 \| 실버 \| 건메탈 |
| usageContext | 데일리 \| 오피스 \| 이브닝 |
| weightGrade | 가벼움 \| 보통 \| 무거움 |
| lockType | 지퍼 \| 플립 \| 마그네틱 |
| internalStorageLevel | 심플 \| 구획많음 |
| laptopCompatible | `"true"` \| `"false"` (문자열) |

---

## 5. 보충 질문 생성 (F4-1)

`structureIntent` 응답의 `needsFollowUp` 이 `true`일 때만 호출.

### `POST /api/consultations/followup-question`

요청:
```json
{
  "utterance": "그냥 비슷한 걸 보여주세요",
  "currentIntent": { "...structuredIntent 그대로..." }
}
```

응답 `200`:
```json
{ "question": "혹시 오늘 바로 구매를 원하시나요, 아니면 여유를 두고 보셔도 괜찮으실까요?" }
```

---

## 6. 보충 답변 반영 재구조화 (F4-2, 최대 1회)

### `POST /api/consultations/followup-answer`

요청:
```json
{
  "utterance": "그냥 비슷한 걸 보여주세요",
  "followUpQuestion": "혹시 오늘 바로 구매를 원하시나요, 아니면 여유를 두고 보셔도 괜찮으실까요?",
  "followUpAnswer": "음.. 딱히 급하진 않아요"
}
```

응답 `200`: `structureIntent`와 동일한 형태. **`needsFollowUp`은 서버가 항상 `false`로 강제한다** (F4 규칙: 보충 질문은 최대 1회).

---

## 7. 의도 확인·수정 (F4)

별도 API 없음. 프론트가 5-6단계에서 받은 `structuredIntent` 를 화면에 그대로 보여주고, CA/고객이 확인 후 값을 고치면(예: `essentialConditions`에서 항목 추가/삭제) 그 수정된 JSON을 그대로 다음 단계(`/decide`) 요청에 넣어 보낸다.

---

## 8. 결정 + Last Intent Card 생성 (F5+F6)

### `POST /api/consultations/decide`

요청:
```json
{
  "storeId": 1,
  "skuId": 1,
  "structuredIntent": { "...최종 확인된 구조화 의도..." }
}
```

응답 `200` (예: 결과1):
```json
{
  "resultType": "EXACT_PRODUCT",
  "coreConditions": "로고가 정면 중앙에 오는 각진 실루엣을 중요하게 보고 계셨습니다.",
  "nextAction": "강남 신세계점 재고를 확인해 안내해 드릴 수 있습니다.",
  "reason": "고객님이 언급하신 로고 위치와 실루엣은 지금 보고 계신 제품에서만 확인되는 특징입니다.",
  "difference": "오늘 바로 구매하지 않으셔도 괜찮다고 하셔서, 동일 제품을 타 매장에서 확보하는 경로를 우선 제안드립니다.",
  "recommendedProduct": null,
  "pathDescription": "강남 신세계점 재고 확인",
  "actionType": "OTHER_STORE_CHECK_REQUEST",
  "actionButtonLabel": "타 매장 확인 요청"
}
```

`resultType` 별 응답 차이:

| resultType | recommendedProduct | actionType | actionButtonLabel |
|---|---|---|---|
| EXACT_PRODUCT | null | OTHER_STORE_CHECK_REQUEST 또는 RESTOCK_CHECK_REQUEST | "타 매장 확인 요청" \| "입고 확인 신청" |
| COMPARISON_EXPERIENCE | 제안 SKU 정보 | PRODUCT_CHECK_REQUEST | "이 제품 확인하기" |
| TODAY_PURCHASE | 제안 SKU 정보 | PRODUCT_CHECK_REQUEST | "이 제품 확인하기" |
| ADDITIONAL_CONSULTATION | null | RECONSULT | "조건 다시 확인하기" |

`recommendedProduct` 형태 (null이 아닐 때):
```json
{ "skuId": 2, "productId": 2, "productName": "MCM 크로스바디 백 스몰", "imageUrl": "https://...", "color": "다크브라운", "size": "스몰" }
```

> **프론트 표시 규칙**: 화면에 실행 버튼은 `actionButtonLabel` 하나만 노출한다 (여러 개 나열 금지). "품절", "대체품", "BEST MATCH", 적합도 %와 같은 표현은 서버 응답에 포함되지 않도록 프롬프트 단에서 금지했지만, 프론트에서도 별도로 이런 문구를 추가하지 않는다.

---

## 9. 실행 요청 + 완료 (F7) & 상담 결과 저장 (F8)

### `POST /api/consultations/execute`

`/decide` 응답 값을 그대로 담아 보낸다.

요청 (결과1 예시):
```json
{
  "customerId": 1,
  "skuId": 1,
  "resultType": "EXACT_PRODUCT",
  "actionType": "OTHER_STORE_CHECK_REQUEST",
  "recommendedSkuId": null,
  "pathDescription": "강남 신세계점 재고 확인",
  "coreConditionsSummary": "로고가 정면 중앙에 오는 각진 실루엣을 중요하게 보고 계셨습니다."
}
```
요청 (결과2/3 예시 — `recommendedSkuId`에 `/decide` 응답의 `recommendedProduct.skuId` 를 넣는다):
```json
{
  "customerId": 1,
  "skuId": 1,
  "resultType": "COMPARISON_EXPERIENCE",
  "actionType": "PRODUCT_CHECK_REQUEST",
  "recommendedSkuId": 2,
  "pathDescription": "현재 매장 비교 체험 제품 확인",
  "coreConditionsSummary": "가죽 소재의 광택을 직접 확인하고 싶어 하셨습니다."
}
```

응답 `200`:
```json
{ "consultationResultId": 5, "completionMessage": "요청이 접수되었습니다. CA가 실제 재고를 확인합니다" }
```

`actionType` → `completionMessage` 매핑:

| actionType | completionMessage |
|---|---|
| OTHER_STORE_CHECK_REQUEST | "요청이 접수되었습니다. CA가 실제 재고를 확인합니다" |
| RESTOCK_CHECK_REQUEST | "확인 신청이 접수되었습니다" |
| PRODUCT_CHECK_REQUEST | "CA에게 제품 확인을 요청했습니다" |
| RECONSULT | "고객의 조건을 다시 확인합니다" |

> 실제 예약/구매/이동이 완료된 것처럼 표시하지 않는다 — 이 메시지들은 전부 "접수/요청" 수준이다.

`/execute` 로 생성된 상담 결과는 `executionStatus="REQUESTED"` 로 시작한다. CA가 이후 실제 확인 결과를 알게 되면 아래 10-1 을 호출해 상태를 갱신한다.

---

## 10. 고객 모바일 상담 결과 조회 (F8)

### `GET /api/consultations/customers/{customerId}?page={page}&size={size}`

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| page | int (query) | 0 | 조회할 페이지 번호 (0부터 시작) |
| size | int (query) | 10 | 한 페이지당 결과 수 |

정렬 기준: `consultedAt` DESC (최신 상담순). 동의하지 않은 고객이면 `403`.

응답 `200`:
```json
{
  "content": [
    {
      "id": 5,
      "skuId": 1,
      "productName": "MCM 백팩 미디움",
      "imageUrl": "https://...",
      "resultType": "EXACT_PRODUCT",
      "recommendedPath": "강남 신세계점 재고 확인",
      "coreConditions": "로고가 정면 중앙에 오는 각진 실루엣을 중요하게 보고 계셨습니다.",
      "consultedAt": "2026-08-16T15:20:00",
      "executionStatus": "REQUESTED",
      "executionNote": null,
      "executionUpdatedAt": "2026-08-16T15:20:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10,
  "first": true,
  "last": true,
  "empty": false
}
```

주요 페이지네이션 필드:

| 필드 | 타입 | 설명 |
|---|---|---|
| content | array | 현재 페이지의 상담 결과 목록 |
| totalElements | long | 전체 상담 결과 수 |
| totalPages | int | 전체 페이지 수 |
| number | int | 현재 페이지 번호 (0부터 시작) |
| size | int | 한 페이지당 크기 |
| first | boolean | 첫 번째 페이지 여부 |
| last | boolean | 마지막 페이지 여부 |
| empty | boolean | 현재 페이지가 비어있는지 여부 |

`executionStatus` 값: `REQUESTED`(요청 접수) \| `UNABLE`(실행 불가) \| `FOLLOW_UP_NEEDED`(후속 확인 필요). 모바일 화면은 이 값에 따라 "확인 중" / "확인 어려움 — 사유: {executionNote}" / "추가 확인 필요 — {executionNote}" 등으로 표시하면 된다.

---

## 10-1. 실행 결과 후속 상태 갱신 (기능명세서 7번)

### `PATCH /api/consultations/{consultationResultId}/execution-status`

CA가 카드 실행 버튼을 누른 뒤, 실제로 확인해 본 결과를 나중에 기록한다. 같은 상담 결과 행을 덮어쓰므로 중복 기록되지 않는다.

요청:
```json
{ "status": "UNABLE", "note": "강남 신세계점도 실제 재고 없음을 확인함" }
```
- `status`: `REQUESTED` \| `UNABLE` \| `FOLLOW_UP_NEEDED`
- `note`: `UNABLE`, `FOLLOW_UP_NEEDED` 인 경우 **필수** (없으면 400 — "사유/안내 없이 완료 처리하지 않는다")

응답 `200`: 갱신된 `ConsultationResultResponse` (위 10번과 동일한 형태, `executionStatus`/`executionNote`/`executionUpdatedAt` 반영).
