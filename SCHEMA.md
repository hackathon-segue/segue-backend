# SCHEMA.md — Segue 데이터 스키마

DB: MySQL 8.x / ORM: Spring Data JPA (Hibernate, `ddl-auto=update`)
엔티티 위치: `src/main/java/com/segue/backend/domain/`

## ERD 개요

```
store (매장)
  └─< inventory >─┐
                   ├─ sku (SKU) >── product (제품)
customer (고객)     │     └── product_attribute (1:1, 매칭용 사전 속성)
  ├─< cart_item >──┘
  ├─< consultation_result >── sku
  └── customer_consent (1:1)
```

- `product` 1 : N `sku` (한 제품에 컬러/사이즈 조합별 SKU 여러 개)
- `sku` 1 : 1 `product_attribute` (매칭 판단용 사전 입력 속성)
- `sku` 1 : N `inventory` (매장별 재고 상태 행 — 매장 수만큼)
- `customer` 1 : N `cart_item`, `customer` 1 : N `consultation_result`
- `customer` 1 : 1 `customer_consent` (고객당 최신 동의 의사 1건, 기능명세서 5번)
- `cart_item`, `consultation_result` 는 `sku` 를 참조 (제품 단위가 아니라 SKU 단위)

---

## 1. store (매장)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(100) | NOT NULL | 매장명 (예: "청담 본점") |

## 2. product (제품)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(200) | NOT NULL | 제품명 |
| image_url | VARCHAR(500) | | 제품 이미지 URL |
| category | VARCHAR(100) | | 카테고리 (백팩/토트백 등) |

## 3. sku (SKU)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| product_id | BIGINT | FK → product.id, NOT NULL | |
| color | VARCHAR(50) | NOT NULL | |
| size | VARCHAR(50) | NOT NULL | |
| material | VARCHAR(200) | | 소재 (표시용 상세 설명) |
| weight_grams | INT | | 무게(g) |
| storage_structure | VARCHAR(200) | | 수납 구조 |
| wear_style | VARCHAR(100) | | 착용 방식 |
| laptop_compatible | BOOLEAN | NOT NULL | 노트북 수납 여부 |

권장 유니크 제약: `(product_id, color, size)` — 같은 제품의 컬러·사이즈 조합은 SKU 1개로 유일해야 함 (F0 저장 시 이 조합으로 SKU 를 찾음).

## 4. product_attribute (사전 입력 속성 — 매칭용)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| sku_id | BIGINT | FK → sku.id, NOT NULL, UNIQUE (1:1) | |
| color_family | VARCHAR(50) | | 컬러 계열 |
| color_tone | VARCHAR(50) | | 컬러 톤 |
| material | VARCHAR(100) | | 소재(정규화된 매칭용 값) |
| gloss_level | VARCHAR(50) | | 광택 수준 |
| logo_visibility | VARCHAR(50) | | 로고 노출도 |
| logo_position | VARCHAR(100) | | 로고 위치 |
| pattern_density | VARCHAR(50) | | 패턴 밀도 |
| silhouette | VARCHAR(100) | | 실루엣 |
| structure | VARCHAR(100) | | 구조 |
| size_grade | VARCHAR(50) | | 크기 등급 |
| strap_type | VARCHAR(100) | | 스트랩 유형 |
| hardware_color | VARCHAR(50) | | 하드웨어 컬러 |
| usage_context | VARCHAR(100) | | 사용 상황 |
| weight_grade | VARCHAR(50) | | 무게 등급 |
| lock_type | VARCHAR(100) | | 잠금 방식 |
| internal_storage_level | VARCHAR(50) | | 내부 수납 수준 |

> 이 16개 컬럼의 **값 어휘(vocabulary)** 는 `segue-backend/src/main/resources/prompts/intent.txt` 에
> AI 프롬프트로 그대로 명시되어 있다. AI가 구조화하는 `essentialConditions` / `preferredConditions` 의
> key-value 는 반드시 이 어휘 안에서만 나오도록 프롬프트로 강제하며, 그래야 규칙 기반 엔진이
> 문자열 비교만으로 정확히 매칭할 수 있다. 어휘를 변경하면 intent.txt / followup.txt / card.txt 와
> DataLoader 더미 데이터를 함께 갱신해야 한다.

## 5. inventory (재고)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| sku_id | BIGINT | FK → sku.id, NOT NULL | |
| store_id | BIGINT | FK → store.id, NOT NULL | |
| current_store_in_stock | BOOLEAN | NOT NULL | 이 행의 store 기준 현재 매장 보유 여부 |
| other_store_in_stock | BOOLEAN | NOT NULL | 타 매장(어딘가) 보유 여부 |
| restock_planned | BOOLEAN | NOT NULL | 입고 예정 여부 |
| confirmed | BOOLEAN | NOT NULL | 기능명세서 6번: 이 재고 상태가 실제로 확인된 값인지 |
| checked_at | DATETIME | NOT NULL | 이 재고 상태의 기준 시점 (신선도 판단용) |

권장 유니크 제약: `(sku_id, store_id)` — SKU 하나당 매장별로 재고 상태 행이 1개.
**재고 수량은 저장하지 않는다.** 판단은 항상 보유 여부(boolean) 기준.

> **재고 신뢰도 게이트 (기능명세서 6번)**: `confirmed=false` 이거나 `checked_at` 이 신선도 기준(기본
> 12시간, `application.properties` 의 `inventory.freshness-hours`)을 넘기면, `DecisionEngine` 은 이
> 값을 "확정 구매 가능 경로"로 사용하지 않고 추가 상담(`ADDITIONAL_CONSULTATION`, reasonCode
> `URGENCY_TODAY_STOCK_UNVERIFIED` / `INVENTORY_UNVERIFIED`)으로 전환한다.
>
> **임계값 12시간 (이슈 #19 결정)**: 신선도를 보는 대상은 재고 수량이 아니라 "CA 가 그 SKU 를 실제로
> 확인했는가"이고, 사람이 개입하는 행위라 하루 1~2회 수준이다. 12시간은 오픈/마감 2회 확인 주기,
> 즉 **직전 근무 교대 이내에 확인된 것만 확정 경로로 인정한다**는 기준이다. 짧게 잡으면 추가 상담이
> 늘 뿐 틀린 정보가 나가지는 않지만 길게 잡으면 빠진 재고를 "확정 가능"으로 안내하게 되므로, 위험이
> 비대칭이라 애매하면 짧은 쪽으로 기울인다. 결과 유형별 차등 기준은 두지 않는다 — 비교 체험도
> "지금 이 매장에 있다"를 전제로 고객을 움직이는 결과이고, 근거 없는 두 번째 숫자가 생기면 규칙
> 엔진의 설명 가능성이 깎인다.
>
> **`other_store_in_stock` 의 신뢰도는 어느 행의 `confirmed`/`checked_at` 을 보는가**: 이 값은
> "현재 매장" 행(예: 청담 본점 행)에 함께 저장된 값이므로, 신뢰도도 **그 현재 매장 행 자신의**
> `confirmed`/`checked_at` 기준으로 판단한다 (강남 신세계점 행 쪽의 `confirmed` 를 바꿔도 영향 없음).
> 페르소나 4(원제품 SKU 1, 청담 본점 행)를 대상으로 청담 행의 `confirmed` 를 0으로 바꾼 뒤 실제
> 재검증한 결과 `EXACT_PRODUCT` → `ADDITIONAL_CONSULTATION`(reasonCode `INVENTORY_UNVERIFIED`)으로
> 정확히 전환되는 것을 확인했다 (이슈 #19).

## 6. customer (고객)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(100) | NOT NULL | 성명 |
| phone_number | VARCHAR(30) | NOT NULL, UNIQUE | F1 조회 키 |
| email | VARCHAR(255) | UNIQUE | 로그인 아이디. 항상 소문자로 정규화해 저장 |
| password | VARCHAR(100) | | BCrypt 해시(60자). 평문 저장·응답 노출 없음 |

> **`email` / `password` 에 NOT NULL 을 걸지 않는 이유**: `ddl-auto=update` 로 기존 행이 있는 테이블에
> 컬럼을 추가하기 때문이다. 컬럼 추가 시점에는 값이 없고, DataLoader 가 기동하면서 시드 계정에
> 채워 넣는다. 값 존재 여부는 요청 DTO 검증(`@NotBlank`)과 서비스 계층에서 보장한다.
>
> **전화번호 중복 검사는 정규화 기준이다.** `010-1234-5678` 과 `01012345678` 은 같은 번호로 취급되어
> 두 계정을 만들 수 없다(409). DB 의 UNIQUE 제약은 문자열 기준이므로 애플리케이션에서 한 번 더 막는다.
>
> **비밀번호는 어떤 응답에도 포함되지 않는다.** 단방향 해시라 복원이 불가능하므로, "내 계정" 화면의
> 비밀번호 표시는 프론트가 고정 마스킹 문자열을 렌더링해야 한다.
>
> 데모용 시드 계정: `kim@segue.test`, `lee@segue.test` (비밀번호 `segue1234`). 이메일은 매 기동마다
> 동기화하고 비밀번호는 값이 없을 때만 채우므로, 프로필 편집으로 바꾼 비밀번호가 재기동 때
> 초기화되지 않는다.

## 7. cart_item (장바구니 항목)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| customer_id | BIGINT | FK → customer.id, NOT NULL | |
| sku_id | BIGINT | FK → sku.id, NOT NULL | |
| color | VARCHAR(50) | NOT NULL | 담을 당시 선택 컬러 (SKU 와 동일값 스냅샷) |
| size | VARCHAR(50) | NOT NULL | 담을 당시 선택 사이즈 |
| saved_at | DATETIME | NOT NULL | F2 "최근 담은 순" 정렬 기준 |

## 8. consultation_result (상담 결과)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| customer_id | BIGINT | FK → customer.id, NOT NULL | |
| sku_id | BIGINT | FK → sku.id, NOT NULL | 상담이 시작된 원제품(품절) SKU |
| result_type | VARCHAR(40) | NOT NULL | `EXACT_PRODUCT` \| `COMPARISON_EXPERIENCE` \| `TODAY_PURCHASE` \| `ADDITIONAL_CONSULTATION` |
| recommended_path | VARCHAR(500) | NOT NULL | 추천 제품 또는 확보 경로 설명 |
| core_conditions | VARCHAR(1000) | NOT NULL | 고객 핵심 조건 요약 (Last Intent Card 문구) |
| consulted_at | DATETIME | NOT NULL | |
| execution_status | VARCHAR(20) | NOT NULL | 기능명세서 7번: `REQUESTED`(요청접수) \| `UNABLE`(실행불가) \| `FOLLOW_UP_NEEDED`(후속확인필요) |
| execution_note | VARCHAR(500) | NULL | `UNABLE`/`FOLLOW_UP_NEEDED` 인 경우 사유·안내 (필수) |
| execution_updated_at | DATETIME | NOT NULL | 실행 상태가 마지막으로 갱신된 시각 |

> **실행 결과 후속 추적 (기능명세서 7번)**: `/execute` 호출 시 `execution_status=REQUESTED` 로 생성되고,
> 이후 CA가 `PATCH /api/consultations/{id}/execution-status` 로 같은 행을 갱신한다 (별도 로그 테이블 없이
> 1건을 덮어쓰는 방식이라 "중복 기록 방지" 요구사항을 자연히 만족). `UNABLE`/`FOLLOW_UP_NEEDED` 로 갱신할
> 때 `execution_note` 없이는 거부된다 (400).

## 9. customer_consent (고객 동의 — 기능명세서 5번)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| customer_id | BIGINT | FK → customer.id, NOT NULL, UNIQUE (1:1) | |
| status | VARCHAR(20) | NOT NULL | `AGREE` \| `DISAGREE` |
| scope | VARCHAR(200) | NOT NULL | 동의 범위 (장바구니 조회·상담결과 저장·모바일 재확인) |
| consented_at | DATETIME | NOT NULL | 동의 의사를 기록한 시각 |

고객 1명당 행 1개만 유지한다 (반복 제출 시 마지막 의사로 덮어씀 — "동일 고객이 반복 제출하면 마지막
의사만 적용" 요구사항). `hasConsented(customerId)` = 레코드가 존재하고 `status=AGREE`.

**동의 게이트가 걸린 API**: `GET /api/cart`(CA의 회원 장바구니 조회), `POST /api/consultations/execute`
(상담 결과의 고객 정보 저장), `GET /api/consultations/customers/{id}`(고객 모바일 재확인). 동의가 없으면
403 `ConsentRequiredException` 을 반환한다.

**게이트 대상이 아닌 API**: `POST /api/cart`(고객 본인의 장바구니 담기), `GET /api/cart/mine`(고객 본인의
쇼핑백 조회). 동의는 **CA 가 고객 데이터를 열람할 때 확인하는 절차**이므로 본인이 자기 데이터를 보는
것은 대상이 아니다.

> CA 경로(`GET /api/cart`)와 본인 경로(`GET /api/cart/mine`)를 **엔드포인트로 분리**했다. 파라미터
> 하나로 분기하면 CA 쪽에서 그 값을 빼먹었을 때 게이트가 조용히 우회되므로, 경로를 나눠 실수로
> 우회되는 경우를 없앴다.

---

## 후보 순위 및 동점 처리 (이슈 #3 결정)

### 필수 조건은 이진 판정을 유지한다

필수 조건은 **전부 일치해야 통과**하고, 하나라도 어긋나면 후보에서 제외한다 (`DecisionEngine` 의
`matched.size() == essential.size()`). 부분 일치를 허용해 "낮은 신뢰도로나마 제안"하는 방식은 쓰지 않는다.

- 부분 일치를 엔진에 넣으면 AI 가 잘못 뽑은 조건만 느슨해지는 것이 아니라 **제대로 뽑힌 조건까지 같이
  느슨해진다.** "노트북이 들어가야 한다"가 부분 일치로 통과해 노트북이 안 들어가는 가방이 추천되는
  사고를 이 구조에서는 막을 수 없다.
- AI 가 필수 조건을 과하게 추출하는 위험은 **F4 의도 확인·수정 단계에서 CA 가 고객과 함께 지우는 것**으로
  막는다. 과추출은 되돌릴 수 있지만 엔진 완화는 되돌릴 수 없다.
- "조건이 애매해 확정하기 어렵다"는 상황은 이미 **결과 4(추가 상담)** 가 담당한다.

점수화 및 신뢰도 임계값 도입은 P2 로 미룬다. 튜닝할 실측 데이터가 페르소나 5개뿐이라 오버피팅이 되고,
점수가 생기면 화면에 노출할 수 없는 숫자(CLAUDE.md 금지 사항)가 판단 근거가 되어 설명력이 떨어진다.

### 동점일 때의 결정 순서

필수 조건을 모두 통과한 후보가 여럿이면 어느 것을 골라도 고객 조건을 어기지 않는다. 그래도 단일 카드
원칙상 하나를 골라야 하므로 아래 순서로 결정한다. 이 순위는 **필수 조건을 통과한 후보 사이에서만**
쓰이며(CLAUDE.md F5 처리 원칙 4), 점수를 만들어 임계값과 비교하지 않는다.

| 순서 | 기준 | 근거 |
| --- | --- | --- |
| ① | 선호 조건을 더 많이 만족 | 기존 동작 |
| ② | 양보 가능 조건까지 더 많이 지켜줌 | 고객에게 양보를 덜 요구 |
| ③ | 필수 조건을 더 여유 있게 충족 | 아래 참고 |
| ④ | 원제품과 전체 속성이 더 많이 겹침 | "보시던 제품과 가장 가까운" 설명 근거 |
| ⑤ | SKU ID 가 작은 후보 | 재현 가능한 결정론 보장 |

이전에는 ①만 보고 동점이면 먼저 나온 후보를 조용히 골랐다. 재현성은 있었지만 "왜 이 가방인가"에 답할
근거가 없었다.

**③ 필수 조건의 여유(headroom)**: 지금은 노트북 수납이 유일한 수용력 성격의 조건이다. 고객이 "노트북이
들어가야 해요"라고만 말하면 `essentialConditions` 에는 `laptopCompatible=true` 만 남고 노트북 크기는 알 수
없다. 16인치까지 들어가는 가방은 13인치 가방이 만족시키는 고객을 **모두** 만족시키지만 그 반대는 성립하지
않으므로, 크기를 모를 때는 여유가 큰 쪽을 제안하는 것이 안전하다.

이 기준이 없으면 ④ 외형 근접도가 먼저 걸려, 원제품과 색이 같다는 이유만으로 수용 범위가 좁은 가방이
선택된다. 실제로 시나리오 C(노트북 필요)에서 정답인 SKU 4(16인치, 블랙) 대신 혼동 후보인 SKU 9(13인치,
꼬냑)가 선택되는 것을 확인해 ③을 추가했다.

---

## MCM 12개 제품 데모 시나리오 (페르소나 1~5)

> **이 페르소나 5개가 데모의 단일 기준이다 (이슈 #33).** CLAUDE.md 9절의 시나리오 A~D 는 실제 MCM
> 제품 12개로 데이터를 교체하기 전에 작성된 구버전이라 발화가 현재 데이터와 맞지 않는다. 발화를
> 두 벌 관리하면 데이터가 바뀔 때마다 다시 어긋나므로, 시연·테스트는 아래 페르소나 발화를 그대로
> 사용한다.
>
> | 구 시나리오 (CLAUDE.md 9절) | 결과 유형 | 대체하는 페르소나 |
> | --- | --- | --- |
> | A. 정확한 제품 우선 | 결과1 정확한 제품 확인 | 페르소나 4 오리지널 고수형 |
> | B. 실물 확인 우선 | 결과2 비교 체험 | 페르소나 1 디자인 유지형 / 페르소나 2 시그니처 유지형 |
> | C. 오늘 구매 우선 | 결과3 오늘 구매 가능 | 페르소나 3 즉시 사용·기능형 |
> | D. 조건 불명확 | 결과4 추가 상담 | 페르소나 5 조건 불명확형 |
>
> 페르소나 발화는 **한 글자도 바꾸지 말고 그대로 입력해야 한다.** 각 발화는 특정 속성 키를 정확히
> 추출하도록 검증된 문장이며, 표현을 바꾸면 다른 속성으로 매핑되어 결과가 달라진다(페르소나 2 의
> "부드러운 구조" 참고).

데모 데이터는 실제 MCM 판매 제품 12개로 구성된다 (`DataLoader.java`). 전부 **SKU 1 "M Diamond 비세토스 레더 믹스 · 꼬냑"**(청담 본점 품절, 강남 신세계점 재고 있음)에서 상담이 시작되고, 나머지 11개 SKU 는 역할이 정해져 있다.

| SKU | 제품 | 역할 | 청담 본점 재고 |
|---|---|---|---|
| 1 | M Diamond 비세토스 레더 믹스 · 꼬냑 | 공통 미보유 기준 제품(원제품) | 없음 (강남 있음) |
| 2 | M Diamond 엠보스드 레더 · 블랙 | 디자인형 정답 | 있음 |
| 3 | M New Liz 비세토스 쇼퍼 · 꼬냑 | 시그니처·소재형 정답 | 있음 |
| 4 | L Aren 비세토스 N/S 토트 · 블랙 | 기능형 정답 (16인치 노트북) | 있음 |
| 5 | S 뮌헨 비세토스 토트 · 꼬냑 | 디자인 혼동 후보 A (실루엣만 유사, 핸들 다름) | 있음 |
| 6 | 미니 Diamond 카프 레더 숄더백 · 블랙 | 디자인 혼동 후보 B (핸들 일부만 유사) | 있음 |
| 7 | S Milla 그레인 가죽 토트 · 오렌지에이드 | 소재 혼동 후보 A (컬러/소재 다름) | 있음 |
| 8 | S Aren 비세토스 듀오 호보 · 블랙 | 시그니처 혼동 후보 B (소재만 같고 인상 다름) | 있음 |
| 9 | M Stark 사이드 스터드 비세토스 백팩 · 꼬냑 | 기능 혼동 후보 A (13인치까지만 지원) | 있음 |
| 10 | M Aren ECONYL 가죽 백팩 · 그린 | 기능 혼동 후보 B (스펙은 맞지만 오늘 매장에 없음) | 없음 (타 매장 있음) |
| 11 | 미니 Tracy 비세토스 레더 믹스 크로스바디 · 꼬냑 | 명확한 비적합 후보 A (용도 다름) | 있음 |
| 12 | S Pina 비세토스 탬버린 백 · 꼬냑 | 명확한 비적합 후보 B (장식적 형태) | 없음 |

매칭에 쓰이는 핵심 속성: `silhouette`(1·2·5=사각), `handleType`(1·2 만 다이아몬드컷아웃, 나머지 전부 일반), `material`/`colorFamily`/`patternDensity`(1·3·5·9·11·12 가 비세토스=캔버스/꼬냑 계열), `laptopMaxInch`(4·10=16, 9=13, 나머지 null).

**SKU 1 의 강남 신세계점 재고는 항상 "있음"으로 고정 시딩한다.** DecisionEngine 이 essential 조건을 만족하는 매장 내 대안을 원제품 확보보다 먼저 찾기 때문에(아래 참고), 페르소나 1·2·3·5 는 이 값과 무관하게 원래 의도대로 동작하고, essential 이 원제품에만 유일하게 성립하는 페르소나 4만 자연스럽게 EXACT_PRODUCT 로 빠진다 — 수동 DB 토글이 필요 없다.

### 페르소나 1. 디자인 유지형 → 결과2 비교 체험 (SKU 2)
> "이 직사각형 형태와 다이아몬드 모양 핸들이 가장 좋아요. 색이나 소재는 달라도 괜찮으니, 그런 제품이 있으면 매장에서 직접 보고 싶어요. 오늘 바로 구매할 필요는 없어요."

essentialConditions={silhouette:사각, handleType:다이아몬드컷아웃}. 이 둘을 모두 만족하는 건 SKU 2 뿐. physicalCheckAttributes 에 색상/소재가 잡히더라도 SKU 2 는 색상이 다르므로(의도적으로) 그 매칭에는 실패하는데, 이 경우 엔진은 "실물 확인 요청은 있었지만 그 속성까지는 안 맞아도, essential 을 만족하는 매장 내 대안이 있다"는 폴백(`ESSENTIAL_MATCH_IN_STORE`)으로 SKU 2 를 제시한다.

### 페르소나 2. MCM 시그니처 유지형 → 결과2 비교 체험 (SKU 3)
> "저는 이 모양보다 꼬냑 비세토스 패턴이 더 중요해요. 형태가 조금 달라도 괜찮고, 부드러운 구조면 더 좋아요. 직접 보고 싶어요."

essential={patternDensity:높음, colorFamily:꼬냑, structure:소프트}, physicalCheck 로 실물 확인을 요청한다.

**"부드러운 구조" 문구가 반드시 필요하다.** 이 문구가 없으면 SKU 3(M New Liz)과 SKU 5(S 뮌헨)가
패턴·컬러 조건을 똑같이 만족해 동점이 되고, 동점 기준 ④(원제품과의 속성 근접도)에서 구조가
`하드`로 원제품과 같은 SKU 5 가 이긴다(SKU 5 는 원제품과 15개 속성 일치, SKU 3 은 11개).
SKU 5 는 페르소나 1 용 혼동 후보이므로 시그니처형의 정답이 되면 안 된다.
`structure=소프트` 가 essential 에 들어가면 SKU 5(하드)가 후보에서 제외되어 SKU 3 만 남는다.

표현도 정확해야 한다. "부드러운" 만 쓰면 AI 가 `silhouette=라운드` 로 매핑하는데 SKU 3 의
실루엣은 `사각` 이라 후보가 사라져 추가 상담으로 빠진다. **"구조"** 라는 단어가 있어야
`structure=소프트` 로 정확히 잡힌다.

### 페르소나 3. 즉시 사용·기능형 → 결과3 오늘 구매 가능 (SKU 4)
> "오늘 출장 전에 꼭 필요하고 16인치 노트북이 들어가야 해요. 디자인은 달라도 괜찮아요."

essentialConditions={laptopMaxInch:"16"}, urgency=TODAY. SKU 9 는 13인치까지만 지원해 essential 에서 제외되고, SKU 10 은 스펙은 맞지만 청담 본점에 오늘 재고가 없어 제외되어, SKU 4 만 남는다.

### 페르소나 4. 오리지널 고수형 → 결과1 정확한 제품 확인 (원제품, 강남 신세계점)

> **이슈 #33**: CLAUDE.md 9절의 구 시나리오 A(정확한 제품 우선)를 이 페르소나가 대체한다.
> 구 발화 "이 로고 위치와 각진 형태가 좋아요" 는 `{logoPosition:정면중앙, silhouette:사각}` 을
> 추출하는데, 이 조합은 SKU 3·4·5·9·11 도 함께 만족해 **원제품에만 유일하게 성립하지 않는다.**
> 그래서 매장 내 후보가 ③-보조 폴백으로 먼저 선택되어 `EXACT_PRODUCT` 에 도달할 수 없었다.
> `EXACT_PRODUCT` 는 구조상 "원제품에만 유일하게 성립하는 필수 조건"이 있어야 나오는 결과다.
> 이 페르소나의 발화는 `{colorFamily:꼬냑, handleType:다이아몬드컷아웃}` 을 추출하는데,
> `handleType=다이아몬드컷아웃` 은 SKU 1·2 만 가지고 SKU 2 는 블랙이라 컬러에서 갈리므로
> 원제품에만 성립한다. 별도 발화를 새로 만들 필요 없이 이 페르소나로 시연하면 된다.
> "이 꼬냑 비세토스 컬러랑 다이아몬드 모양 핸들이 그대로인 제품이어야 해요. 색이나 소재가 다른 건 원하지 않아요. 오늘 아니어도 되니까, 다른 매장에 있으면 거기서 받아보고 싶어요."

essentialConditions={colorFamily:꼬냑, handleType:다이아몬드컷아웃}(경우에 따라 material 도 포함). 색상까지 필수로 고정되면 SKU 2(블랙이라 탈락)를 포함해 카탈로그의 어떤 대안도 전부 만족시키지 못하므로, 원제품 자체를 확보하는 경로(강남 신세계점 재고 확인)로 간다.

### 페르소나 5. 조건 불명확형 → 결과4 추가 상담
> "그냥 비슷한 느낌의 가방이면 다 좋아요. 딱히 정해둔 건 없어서 뭐가 맞는지 저도 잘 모르겠어요."

essential/physicalCheck 모두 비어 있고 목적·시급성도 불명확 → `needsFollowUp=true` → F4 보충 질문 1회. 보충 답변 이후에도 canWait/canVisitOtherStore 가 여전히 null(=false 로 간주)이면 원제품 확보 조건도 성립하지 않아 추가 상담으로 연결된다.

---

이 매핑은 어디까지나 데모 재현을 위한 더미 데이터 설계이며, 실제 결정 로직 자체(`DecisionEngine.java`)는 특정 페르소나를 하드코딩하지 않고 우선순위 규칙(①필수 ②시급성 ③실물확인(+매장 내 essential 일치 폴백) ④확보경로 ⑤선호)만으로 동작한다.

모든 inventory 행은 `confirmed=true`, `checked_at=기동 시각`으로 시딩되어 있어 재고 신뢰도 게이트(위 5번 섹션)가 페르소나 1~5 결과에 영향을 주지 않는다. 김세계는 `customer_consent.status=AGREE` 로 미리 동의되어 있어 장바구니 조회-Last Intent 플로우를 바로 데모할 수 있고, 이수현은 동의 기록이 아예 없어 `GET /api/cart` 호출 시 403(동의 필요) 차단 흐름도 그대로 데모할 수 있다.

> **LLM 응답 변동성 주의**: intent 구조화는 매 호출 실제 OpenAI API를 타므로, 동일 발화라도 essentialConditions/physicalCheckAttributes 로 잡히는 속성 key 가 호출마다 약간 달라질 수 있다 (예: 페르소나 2 에서 드물게 `patternDensity`+`silhouette` 조합으로 잡혀 SKU 5 가 선택된 사례 관찰됨). few-shot 예시 보강은 이슈 #14 로 추적 중.
