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
> `URGENCY_TODAY_STOCK_UNVERIFIED` / `INVENTORY_UNVERIFIED`)으로 전환한다. 임계값은 GitHub 이슈
> #19 에서 팀 결정 대상으로 추적 중이다.

## 6. customer (고객)

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(100) | NOT NULL | |
| phone_number | VARCHAR(30) | NOT NULL, UNIQUE | F1 조회 키 |

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
403 `ConsentRequiredException` 을 반환한다. `POST /api/cart`(고객 본인의 장바구니 담기)는 고객 자신의
행위이므로 게이트 대상이 아니다.

---

## 시나리오 A·B·C·D 성립 근거

4가지 데모 시나리오는 전부 **동일한 원제품 SKU** (`sku.id=1`, "MCM 백팩 미디움" 블랙/미디움, 청담 본점 품절 · 강남 신세계점 보유)에서 출발하되, CA가 입력하는 고객 발화에 따라 F5 규칙 엔진의 결과가 갈리도록 나머지 SKU(2~5)의 속성/재고를 아래처럼 설계했다 (`DataLoader.java` 참조).

| SKU | 제품 | 소재/광택 | 로고위치/실루엣 | 노트북 수납 | 청담 본점 재고 |
|---|---|---|---|---|---|
| S1 (원제품) | MCM 백팩 미디움 | 가죽 / 중간 | 정면중앙 / 각진 | O | **없음** (강남점 있음) |
| S2 | MCM 크로스바디 백 스몰 | 가죽 / 중간 | 스트랩 / 라운드 | X | 있음 |
| S3 | MCM 토트백 라지 | 캔버스 / 낮음 | 정면하단 / 사각 | O | 있음 |
| S4 | MCM 숄더백 미니 | 패브릭 / 낮음 | 스트랩 / 라운드 | X | 있음 |
| S5 | MCM 벨트백 | 가죽 / 높음 | 스트랩 / 라운드 | X | 없음 (입고 예정만) |

- **시나리오 A** ("로고 위치와 각진 형태가 좋아요. 오늘 살 필요는 없어요")
  → essentialConditions = {logoPosition:정면중앙, silhouette:각진}. S1 자신이 이 조건을 만족하고, 카탈로그의 다른 SKU 는 아무도 이 조합을 만족하지 않는다. urgency=FLEXIBLE, canWait=true → **결과1 정확한 제품 확인**, 강남 신세계점 재고 확인 경로.

- **시나리오 B** ("원제품이 아니어도 괜찮고 가죽의 광택을 직접 보고 싶어요")
  → essentialConditions={} (원제품 고정 아님), physicalCheckAttributes=[material, glossLevel]. S1 의 material=가죽/glossLevel=중간과 동일한 값을 가지면서 **청담 본점에 재고가 있는** SKU 는 S2 뿐. → **결과2 비교 체험 제품**(S2).

- **시나리오 C** ("출장 전에 오늘 꼭 필요하고 노트북이 들어가야 해요")
  → essentialConditions={laptopCompatible:"true"}, urgency=TODAY. 청담 본점에 오늘 재고가 있으면서 노트북 수납이 되는 SKU 는 S3 뿐(S1 은 원제품이라 후보 풀에서 제외, 청담 본점에 없음). → **결과3 오늘 구매 가능한 제품**(S3).

- **시나리오 D** ("그냥 비슷한 걸 보여주세요")
  → essential/physicalCheck 모두 비어 있고 목적·시급성도 불명확 → `needsFollowUp=true` → F4 보충 질문 1회 실시. 보충 답변 이후에도 canWait/canVisitOtherStore 가 여전히 불명확(=false 로 간주)하면, 원제품 확보 조건(④)도 성립하지 않으므로 → **결과4 추가 상담**.

이 매핑은 어디까지나 데모 재현을 위한 더미 데이터 설계이며, 실제 결정 로직 자체(`DecisionEngine.java`)는 특정 시나리오를 하드코딩하지 않고 우선순위 규칙(①필수 ②시급성 ③실물확인 ④확보경로 ⑤선호)만으로 동작한다.

모든 inventory 행은 `confirmed=true`, `checked_at=기동 시각`으로 시딩되어 있어 재고 신뢰도 게이트(위 5번 섹션)가 A~D 결과에 영향을 주지 않는다. 김세계는 `customer_consent.status=AGREE` 로 미리 동의되어 있어 장바구니 조회~Last Intent 플로우를 바로 데모할 수 있고, 이수현은 동의 기록이 아예 없어 `GET /api/cart` 호출 시 403(동의 필요) 차단 흐름도 그대로 데모할 수 있다.
