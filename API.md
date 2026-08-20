# API.md — Segue Backend API 명세

Base URL (로컬): `http://localhost:8080`
모든 요청/응답은 `application/json`.

CORS는 환경별로 다르다. 개발은 프론트 개발 서버 포트가 매번 바뀌므로 모든 origin 을 허용하고,
운영(`prod` 프로필)은 `application-prod.properties` 의 `cors.allowed-origins` 에 적힌 주소만 허용한다.

에러 응답 공통 포맷:
```json
{ "message": "사람이 읽을 수 있는 에러 설명" }
```
- 404: 리소스 없음 (예: 고객/SKU 조회 실패)
- 400: 요청 값 검증 실패 (Bean Validation)
- 401: 로그인 실패 / 현재 비밀번호 불일치
- 403: 고객 동의가 필요함 (기능명세서 5번, 아래 "동의 관리" 참고)
- 409: 이메일·전화번호 중복
- 502: AI(OpenAI) 호출 실패

### 400 Bad Request — Bean Validation 실패 예시

필수 필드 누락, 타입 불일치 등 `@Valid` 검증에 실패하면 400을 반환한다. `message`에는 실패한 필드명과 사유가 세미콜론(`;`)으로 구분되어 포함된다.

```json
{ "message": "customerId: must not be null; color: must not be blank" }
```

---

## 전체 플로우 순서 (프론트 구현 가이드)

0. **고객 모바일**: `POST /api/customers/signup` 또는 `POST /api/customers/login` (응답의 `id` 를 `customerId` 로 보관) → `GET /api/products` 로 제품 목록 브라우징 → `GET /api/products/{id}` 로 제품 상세(컬러·사이즈 옵션) 확인
1. **고객 모바일**: `POST /api/cart` 로 컬러/사이즈 선택 후 담기 (본인 쇼핑백 확인은 `GET /api/cart/mine`)
2. **태블릿**: `GET /api/customers/lookup` 로 고객 조회
3. 응답의 `hasConsented` 가 `false` 면 데이터 이용 동의 화면을 먼저 보여주고 `POST /api/customers/{id}/consent` 로 기록
4. `GET /api/cart` 로 장바구니+재고 확인 (동의 안 된 고객이면 403)
5. 품절 SKU 하나 선택 → `POST /api/consultations/intent` 로 발화 구조화
6. `needsFollowUp=true` 면 `POST /api/consultations/followup-question` → 고객 답변 받기 → `POST /api/consultations/followup-answer`
7. CA/고객이 구조화된 조건을 화면에서 확인·수정 (프론트 로컬 상태 편집, 별도 API 없음)
8. `POST /api/consultations/decide` 호출 (이 호출 동안 프론트는 "AI 분석 중" 로딩 상태만 표시, 별도 화면 전환 없음)
9. Last Intent Card 표시 → 실행 버튼 탭 → `POST /api/consultations/execute`
10. 완료 메시지 표시. 이후 CA가 실제 확인 결과를 알게 되면 `PATCH /api/consultations/{id}/execution-status` 로 후속 상태 갱신
11. **고객 모바일**: `GET /api/consultations/customers/{customerId}?page=0&size=10` 으로 결과 및 현재 처리 상태 확인 (응답은 배열)

> **무상태 설계**: 서버는 상담 진행 중 상태를 세션으로 들고 있지 않는다. 5-9 단계에서 서버가 응답한 값(특히 `structuredIntent`, `decide` 응답 전체)은 프론트가 들고 있다가 다음 요청에 그대로 담아 보내야 한다.

---

## 0. 제품 목록/상세 조회 (F0)

고객 모바일에서 장바구니에 담기 전, 제품을 브라우징하고 컬러/사이즈를 고르는 단계. DB 기반이며 프론트에서 더미로 만들지 않는다.

### `GET /api/products`

응답 `200`:
```json
[
  { "id": 1, "name": "MCM 백팩 미디움", "imageUrl": "https://...", "category": "백팩" },
  { "id": 2, "name": "MCM 크로스바디 백 스몰", "imageUrl": "https://...", "category": "크로스바디" }
]
```
> **목록 응답에는 `price` 가 없다.** 가격은 아래 상세 조회에만 포함되므로, 목록 화면에 가격을
> 표시하려면 제품별로 `GET /api/products/{id}` 를 한 번 더 호출해야 한다.

### `GET /api/products/{productId}`

제품 상세 + 이 제품이 가진 모든 컬러·사이즈 SKU 옵션.

응답 `200`:
```json
{
  "id": 1,
  "name": "MCM 백팩 미디움",
  "imageUrl": "https://...",
  "category": "백팩",
  "price": 1590000,
  "options": [
    {
      "skuId": 1,
      "color": "블랙",
      "size": "미디움",
      "material": "그레인 카프스킨 가죽",
      "weightGrams": 650,
      "storageStructure": "지퍼형 메인 수납 + 노트북 슬리브",
      "wearStyle": "백팩(양쪽 숄더)",
      "laptopCompatible": true
    }
  ]
}
```
응답 `404`: 제품 없음.

프론트는 `options` 중 고객이 고른 `color`/`size`를 그대로 `POST /api/cart` 요청의 `color`/`size`에 넣으면 된다 (아래 F0 장바구니 저장 참고).

---

## 0-1. CA 수동 제품 검색 (Issue #5)

CA가 태블릿에서 제품을 수동 검색하는 백업 API. 제품명을 기본 조건으로, 선택적으로 SKU의 컬러/사이즈 조건을 추가할 수 있다. 컬러/사이즈를 지정하면 해당 조건에 맞는 SKU 옵션만 결과에 포함되고, 매칭 SKU가 없는 제품은 결과에서 제외된다.

### `GET /api/products/search`

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string (query) | ✅ | 제품명 검색어 (부분 일치, 대소문자 무시) |
| color | string (query) | ❌ | SKU 컬러 필터 (정확 일치, 대소문자 무시) |
| size | string (query) | ❌ | SKU 사이즈 필터 (정확 일치, 대소문자 무시) |

응답 `200`:
```json
[
  {
    "id": 1,
    "name": "MCM 백팩 미디움",
    "imageUrl": "https://...",
    "category": "백팩",
    "options": [
      {
        "skuId": 1,
        "color": "블랙",
        "size": "미디움",
        "material": "그레인 카프스킨 가죽",
        "weightGrams": 650,
        "storageStructure": "지퍼형 메인 수납 + 노트북 슬리브",
        "wearStyle": "백팩(양쪽 숄더)",
        "laptopCompatible": true
      }
    ]
  }
]
```
- 검색 결과가 없으면 빈 배열 `[]` 을 반환한다 (404가 아님).
- `options` 배열에는 컬러/사이즈 필터를 통과한 SKU만 포함된다.
- 컬러/사이즈를 지정하지 않으면 해당 제품의 모든 SKU 옵션이 포함된다.

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

## 1-2. 고객 회원가입 / 로그인 / 프로필 (고객 모바일)

세션·토큰을 발급하지 않는다. 로그인 응답의 `id` 를 프론트가 보관해 이후 요청의 `customerId` 로 사용한다.
새로고침하면 사라지므로 `localStorage` 등에 저장하는 것을 권한다.

> **비밀번호는 어떤 응답에도 포함되지 않는다.** 단방향 해시(BCrypt)로 저장해 복원이 불가능하다.
> "내 계정" 화면의 비밀번호 표시는 프론트에서 고정 마스킹 문자열(`••••••••`)을 렌더링해야 한다.

데모용 테스트 계정:

| 이메일 | 비밀번호 | 고객 |
|---|---|---|
| `kim@segue.test` | `segue1234` | 김세계 (동의 완료, 장바구니 있음) |
| `lee@segue.test` | `segue1234` | 이수현 (동의 흐름 데모용) |

### `POST /api/customers/signup`

요청:
```json
{ "name": "박도윤", "email": "park@example.com", "password": "segue1234", "phoneNumber": "010-5555-6666" }
```
- `phoneNumber` **필수**. CA 가 태블릿에서 고객을 조회하는 유일한 키(F1)이므로, 없으면 상담 플로우를 시작할 수 없다.
- `password` 는 **8자 이상**.
- `email` 은 대소문자를 구분하지 않는다 (소문자로 정규화해 저장).

응답 `201`:
```json
{ "id": 3, "name": "박도윤", "phoneNumber": "010-5555-6666", "email": "park@example.com", "hasConsented": false }
```
가입 직후 `hasConsented` 는 항상 `false` 다. 데이터 이용 동의는 매장에서 CA 가 받는다 (기능명세서 5번).

응답 `409`: `{ "message": "이미 사용 중인 이메일 주소입니다." }` 또는 `{ "message": "이미 사용 중인 전화번호입니다." }`

> 전화번호 중복은 **정규화 기준**이다. `010-1234-5678` 이 이미 있으면 `01012345678` 로도 가입할 수 없다.

### `POST /api/customers/login`

요청:
```json
{ "email": "kim@segue.test", "password": "segue1234" }
```
응답 `200`: 위 signup 응답과 동일한 형태.

응답 `401`:
```json
{ "message": "이메일 또는 비밀번호가 일치하지 않습니다." }
```
> **실패 사유를 구분하지 않는다.** "없는 이메일"과 "틀린 비밀번호"를 나누면 특정 이메일의 가입 여부를
> 확인할 수 있게 되므로 동일한 문구를 반환한다. 화면에도 이 한 문구만 표시하면 된다.

### `PATCH /api/customers/{customerId}` — 프로필 편집

요청 (네 필드 모두 필수):
```json
{
  "name": "박도윤",
  "email": "park2@example.com",
  "phoneNumber": "010-5555-9999",
  "currentPassword": "segue1234"
}
```
> **`currentPassword` 는 본인 확인용이며 비밀번호를 바꾸는 값이 아니다.** 토큰 기반 인가가 없어
> `customerId` 를 요청에 담아 보내는 구조라, 이 확인이 없으면 `customerId` 만 바꿔 보내는 것으로
> 남의 계정 정보를 수정할 수 있다. 비밀번호 변경은 아래 별도 엔드포인트를 쓴다.

응답 `200`: 갱신된 고객 정보.
응답 `401`: `{ "message": "현재 비밀번호가 일치하지 않습니다." }`
응답 `409`: 다른 고객이 이미 쓰는 이메일·전화번호. 본인의 기존 값을 그대로 보내는 것은 허용된다.

### `PATCH /api/customers/{customerId}/password` — 비밀번호 변경

요청:
```json
{ "currentPassword": "segue1234", "newPassword": "newpass1234" }
```
- `newPassword` 는 **8자 이상**.

응답 `200`: 갱신된 고객 정보 (비밀번호는 포함되지 않음).
응답 `401`: `{ "message": "현재 비밀번호가 일치하지 않습니다." }`

### 주문 내역

주문 기능은 구현하지 않는다 (CLAUDE.md P2 결제/배송 연동). "내 계정" 화면의 주문 내역은
**빈 상태 고정**으로 표시한다 ("이 계정에 대한 주문 기록이 없습니다").

---

## 2. 장바구니 저장 (F0)

### `POST /api/cart`

요청:
```json
{ "customerId": 1, "productId": 1, "color": "블랙", "size": "미디움" }
```
- `productId` + `color` + `size` 조합으로 서버가 SKU를 찾아 저장한다 (일치하는 SKU가 없으면 404).

응답 `404` (제품 자체가 존재하지 않는 경우):
```json
{ "message": "제품을 찾을 수 없습니다. productId=999" }
```

응답 `404` (제품은 존재하지만 요청한 컬러/사이즈 조합이 없는 경우):
```json
{ "message": "선택한 컬러/사이즈 조합(화이트/라지)은 존재하지 않습니다. 선택 가능한 옵션: 블랙/미디움" }
```
해당 제품에 실제로 등록된 SKU의 컬러/사이즈 조합을 함께 안내한다.

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

## 3-1. 고객 본인 쇼핑백 조회

### `GET /api/cart/mine?customerId={customerId}&storeId={storeId}`

응답 형태는 아래 `GET /api/cart` 와 동일하다. **차이는 동의 게이트뿐이다.**

| 엔드포인트 | 용도 | 동의 게이트 |
|---|---|---|
| `GET /api/cart` | CA 가 태블릿에서 고객 장바구니 조회 (F2) | **있음** (없으면 403) |
| `GET /api/cart/mine` | 고객이 자기 쇼핑백 조회 | 없음 |

동의는 CA 가 고객 데이터를 열람할 때 확인하는 절차이므로, 고객 본인이 자기 쇼핑백을 보는 것은
대상이 아니다. 고객 모바일에서는 `/mine` 을 사용해야 하며, `/api/cart` 를 쓰면 동의 전 고객에게 403 이 뜬다.

`storeId` 는 선택이다. 고객 모바일에는 매장 문맥이 없으므로 생략하면 재고 필드가 전부 `false` 로 내려간다.

---

## 4. 고객 의도 구조화 (F3)

### `POST /api/consultations/intent`

요청:
```json
{ "storeId": 1, "skuId": 1, "utterance": "이 꼬냑 비세토스 컬러랑 다이아몬드 모양 핸들이 그대로인 제품이어야 해요. 색이나 소재가 다른 건 원하지 않아요. 오늘 아니어도 되니까, 다른 매장에 있으면 거기서 받아보고 싶어요" }
```

> 위 발화는 **페르소나 4(오리지널 고수형)** 의 것이다. 데모·테스트 발화는 SCHEMA.md 의 페르소나 1~5 를
> 단일 기준으로 사용한다 (이슈 #33).

응답 `200`:
```json
{
  "structuredIntent": {
    "purpose": "",
    "essentialConditions": { "colorFamily": "꼬냑", "handleType": "다이아몬드컷아웃" },
    "preferredConditions": {},
    "negotiableConditions": {},
    "purchaseUrgency": "FLEXIBLE",
    "physicalCheckAttributes": [],
    "canWait": true,
    "canVisitOtherStore": true,
    "needsFollowUp": false
  },
  "needsFollowUp": false
}
```

> `canWait` / `canVisitOtherStore` 는 고객이 **명시적으로 말한 경우에만** true/false 가 되고, 언급이 없으면
> `null` 입니다. 위 예시는 "오늘 당장 필요하진 않다"는 대기 가능 신호만 있고 타 매장 방문 의사는 말하지
> 않은 경우입니다. 프론트는 이 세 값(`true`/`false`/`null`)을 모두 처리해야 합니다.

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
| colorFamily | 블랙 \| 브라운 \| 베이지 \| 꼬냑 \| 오렌지 \| 그린 |
| colorTone | 웜 \| 쿨 \| 뉴트럴 |
| material | 가죽 \| 캔버스 \| 패브릭 |
| glossLevel | 높음 \| 중간 \| 낮음 |
| logoVisibility | 높음 \| 중간 \| 낮음 |
| logoPosition | 정면중앙 \| 정면하단 \| 스트랩 |
| patternDensity | 높음 \| 중간 \| 낮음 |
| silhouette | 라운드 \| 사각 |
| structure | 하드 \| 소프트 |
| sizeGrade | 미니 \| 스몰 \| 미디움 \| 라지 |
| strapType | 체인스트랩 \| 패브릭스트랩 \| 패브릭+레더콤보 \| 벨트스트랩 |
| hardwareColor | 골드 \| 실버 \| 건메탈 |
| usageContext | 데일리 \| 오피스 \| 이브닝 |
| weightGrade | 가벼움 \| 보통 \| 무거움 |
| lockType | 지퍼 \| 플립 \| 마그네틱 |
| internalStorageLevel | 심플 \| 구획많음 |
| handleType | 다이아몬드컷아웃 \| 일반 |
| laptopCompatible | `"true"` \| `"false"` (문자열) |
| laptopMaxInch | `"13"` \| `"16"` (문자열) |

> **`silhouette` 에 `각진` 은 없다.** 고객이 "각진", "직사각형", "네모난" 처럼 말해도 AI 는 전부
> `사각` 으로 매핑한다. 프론트 선택지에도 `각진` 을 넣지 않는다.
>
> **이 표는 `prompts/intent.txt` 의 어휘 목록과 1:1 로 같아야 한다.** 프론트의 조건 확인·수정
> 화면이 이 표를 기준으로 라벨과 선택지를 만들기 때문에, 여기 없는 key 는 화면에 영문 그대로
> 노출되고 여기 없는 value 는 수정 화면에서 빈 선택지로 뜬다. 어휘를 바꿀 때는 intent.txt,
> 이 표, SCHEMA.md 의 `product_attribute` 표를 함께 고쳐야 한다.

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

**응답은 배열이다.** 다른 목록 API(`/api/products`, `/api/cart` 등)와 동일한 형태이며, 페이징 메타데이터를 감싸는 객체를 반환하지 않는다. `page`/`size` 로 잘라 오는 동작은 그대로 유지된다.

응답 `200`:
```json
[
  {
    "id": 5,
    "skuId": 1,
    "productName": "M Diamond 비세토스 레더 믹스",
    "imageUrl": "/images/products/bag1.png",
    "resultType": "EXACT_PRODUCT",
    "recommendedPath": "강남 신세계점 재고 확인",
    "coreConditions": "다이아몬드 컷아웃 핸들과 캔버스 소재를 반드시 유지하고자 하십니다.",
    "consultedAt": "2026-08-19T15:20:00",
    "executionStatus": "REQUESTED",
    "executionNote": null,
    "executionUpdatedAt": "2026-08-19T15:20:00"
  }
]
```
- 결과가 없으면 빈 배열 `[]` 을 반환한다.
- `executionNote` 는 `REQUESTED` 상태에서 `null` 이다 (`UNABLE`/`FOLLOW_UP_NEEDED` 일 때만 값이 있음).
- 전체 개수를 알아야 하면 `size` 를 충분히 크게 주고 배열 길이를 사용한다. 데모 데이터 규모에서는
  페이징 UI 가 필요하지 않다.

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
