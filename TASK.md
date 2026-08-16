# TASK.md — 파트별 업무 분담

> 이 저장소(`segue-backend/`)는 **백엔드 + AI** 두 파트를 담당한다. Flutter Web(고객 모바일 + 태블릿)은 별도 담당자가 진행하며, 이 문서는 세 파트가 서로 무엇을 주고받아야 하는지 정리한 것이다.

## segue-ai 모듈 구조에 대한 결정

CLAUDE.md 는 AI 파트를 `segue-ai` 로 별도 언급하지만, "백엔드에서 호출할 수 있도록 AI 서비스 모듈화" 요구사항을 만족시키기 위해 **별도 프로세스/저장소가 아니라 `segue-backend` 안의 독립 패키지**(`com.segue.backend.ai`)로 구현했다.

- `ai/OpenAiClient.java` — OpenAI Chat Completions(JSON mode) 호출 저수준 클라이언트
- `ai/PromptLoader.java` — `resources/prompts/*.txt` 로딩
- `ai/AiService.java` — 컨트롤러/서비스가 사용하는 **유일한 진입점** (intent/followup/card 3개 메서드)
- `resources/prompts/intent.txt`, `followup.txt`, `card.txt` — 프롬프트 원문

AI 담당자는 이 4개 파일 + 3개 프롬프트만 수정하면 되고, 나머지 컨트롤러/서비스/엔티티는 백엔드 담당자 영역이다. Java 기반 AI 모듈이 부담스럽다면 이후 이 패키지를 별도 FastAPI/Express 서비스로 분리하고 `AiService` 내부 구현만 HTTP 호출로 교체하면 되도록 인터페이스를 얇게 설계했다.

---

## 기능별 담당 및 의존관계

| 기능 | 담당 | 선행 조건 | 산출물 |
|---|---|---|---|
| F0. 장바구니 저장 | 백엔드(API) + 프론트(UI) | 스키마 확정, `POST /api/cart` 명세 | `CartController`, `CartService` |
| F1. 고객 조회 | 백엔드(API) + 프론트(UI) | 스키마 확정, `GET /api/customers/lookup` 명세 | `CustomerController` |
| F2. 장바구니/재고 확인 | 백엔드(API) + 프론트(UI) | F0/F1 완료, `GET /api/cart` 명세 | `CartService.getCart` |
| F3. 고객 의도 구조화 | AI(프롬프트) + 백엔드(API 연동) | intent.txt 어휘 확정 (SCHEMA.md 의 product_attribute 값과 반드시 일치) | `AiService.structureIntent`, `POST /api/consultations/intent` |
| F4. 보충 질문/확인 | AI(프롬프트) + 백엔드(API) + 프론트(수정 UI) | F3 완료 | `POST /api/consultations/followup-question`, `/followup-answer` |
| F5. 의사결정 엔진 | 백엔드(순수 규칙 로직, AI 미개입) | 재고/속성 더미 데이터, F3/F4 의 구조화 스키마 확정 | `DecisionEngine.java` |
| F6. Last Intent Card | AI(프롬프트) + 백엔드(F5 결과 전달) | F5 완료 | `AiService.generateCard`, `POST /api/consultations/decide` |
| F7. 실행 요청 완료 | 백엔드(API) + 프론트(UI) | F6 완료, 버튼/문구 매핑 확정(API.md) | `POST /api/consultations/execute` |
| F8. 상담 결과 저장/조회 | 백엔드(API) + 프론트(UI) | F7 완료 | `consultation_result` 저장, `GET /api/consultations/customers/{id}` |

## 순서 의존관계 (누가 먼저 끝나야 다음이 가능한가)

1. **스키마 확정(SCHEMA.md)** → 백엔드 Entity/Repository, AI 프롬프트의 속성 어휘, 프론트 화면 데이터 모델이 전부 이 스키마를 기준으로 삼는다. **가장 먼저 고정되어야 함.**
2. **더미 데이터(DataLoader) 확정** → 프론트가 실제 데모 시나리오 A-D 를 화면에서 재현하려면 어떤 고객/전화번호/SKU 로 테스트해야 하는지 알아야 한다. (연락처: 테스트 고객 "김세계" 010-1234-5678)
3. **API.md 확정** → 프론트는 API.md 의 요청/응답 필드만 보고 UI 를 만들 수 있어야 하며, 백엔드/AI 내부 구현 변경이 API.md 응답 형태를 깨지 않는 한 프론트 작업과 병렬 진행 가능.
4. **F5 결정 엔진은 F3/F4 의 `StructuredIntentDto` JSON 스키마에 의존**하므로, AI 프롬프트가 산출하는 필드명이 바뀌면 엔진도 함께 바뀌어야 한다. 이 계약은 `dto/StructuredIntentDto.java` 하나로 고정되어 있다.
5. F6(카드 생성)은 F5(DecisionEngine)가 만든 `DecisionResult` 를 검증된 사실로 받아 문장을 생성할 뿐이므로, F5 없이는 F6 를 테스트할 수 없다.
6. 프론트는 F0-F2(장바구니/재고)를 먼저 붙이고, 이후 F3-F8(Last Intent 플로우)을 순서대로 붙이는 것을 권장. `/decide` 응답에 필요한 필드는 그대로 `/execute` 요청에 되돌려 보내면 되므로 프론트가 별도 세션 상태를 서버에 만들 필요는 없다(무상태 설계, API.md 참고).

---

## 기능명세서.md 재검토로 추가된 3개 기능

CLAUDE.md 에는 없었지만 원본 `기능명세서.md`(🔴 높음·🟡 중간 중요도)에 있던 기능을 추가로 구현했다. 세 가지 모두 프론트 화면이 새로 필요하다.

| 기능 | 담당 | 프론트에 필요한 것 |
|---|---|---|
| 고객 동의 관리 (기능명세서 5번) | 백엔드(API) + 프론트(동의 화면) | 고객 조회 응답의 `hasConsented=false` 면 동의 안내/선택 화면을 띄우고 `POST /api/customers/{id}/consent` 호출 후에만 장바구니 조회로 진행 |
| 재고 신뢰도 표시 (기능명세서 6번) | 백엔드(엔진 로직, API 변경 없음) | 별도 화면 불필요. `ADDITIONAL_CONSULTATION` 결과가 나오는 이유 중 하나가 "재고 정보 미확인"일 수 있다는 점만 인지 |
| 실행 결과 후속 추적 (기능명세서 7번) | 백엔드(API) + 프론트(상태 표시) | 태블릿에 CA가 나중에 실행 결과를 갱신하는 화면(`PATCH .../execution-status`), 고객 모바일 결과 화면에 `executionStatus`/`executionNote` 표시 |

자세한 API 형태는 API.md 의 "1-1. 고객 동의 관리", "10-1. 실행 결과 후속 상태 갱신" 섹션 참고.
