package com.segue.backend.engine;

import com.segue.backend.domain.ProductAttribute;
import com.segue.backend.domain.Sku;
import com.segue.backend.domain.enums.ResultType;
import com.segue.backend.dto.StructuredIntentDto;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * F5 규칙 기반 의사결정 엔진.
 *
 * 판단 우선순위: ① 필수 조건 ② 구매 시급성 ③ 실물 확인 필요 ④ 타 매장 이동/대기 가능 여부 ⑤ 선호 조건.
 * AI 는 이 엔진의 산출물을 문장으로 풀어 설명할 뿐, 결과 자체를 선택하지 않는다.
 */
@Component
public class DecisionEngine {

    /**
     * 후보 간 "원제품과의 근접도"를 셀 때 사용하는 전체 속성 키 (attributeValue 가 해석 가능한 키와 동일).
     * 이슈 #3 동점 처리 기준 ③에서만 쓰이며, 필수/선호 조건 판정에는 관여하지 않는다.
     */
    private static final List<String> ALL_ATTRIBUTE_KEYS = List.of(
            "colorFamily", "colorTone", "material", "glossLevel", "logoVisibility", "logoPosition",
            "patternDensity", "silhouette", "structure", "sizeGrade", "strapType", "hardwareColor",
            "usageContext", "weightGrade", "lockType", "internalStorageLevel",
            "laptopCompatible", "laptopMaxInch", "handleType");

    public DecisionResult decide(DecisionInput input) {
        StructuredIntentDto intent = input.getIntent();
        Map<String, String> essential = safeMap(intent.getEssentialConditions());
        Map<String, String> preferred = safeMap(intent.getPreferredConditions());
        Map<String, String> negotiable = safeMap(intent.getNegotiableConditions());

        // 원제품이 필수 조건을 스스로 만족하는지 (결과 1 후보 자격)
        List<String> originalMatchedKeys = matchedKeys(input.getOriginalAttribute(), input.getOriginalSku(), essential);
        boolean originalSatisfiesEssential = originalMatchedKeys.size() == essential.size();

        // 카탈로그 후보 중 필수 조건을 만족하는 것만 남긴다 (① 필수 조건)
        List<Candidate> essentialCandidates = new ArrayList<>();
        for (Candidate c : input.getCandidates()) {
            List<String> matched = matchedKeys(c.getAttribute(), c.getSku(), essential);
            if (matched.size() == essential.size()) {
                essentialCandidates.add(c);
            }
        }

        boolean anyPathAtAll = originalSatisfiesEssential || !essentialCandidates.isEmpty();
        if (!anyPathAtAll) {
            return additionalConsultation("NO_CANDIDATE_MATCHES_ESSENTIAL");
        }

        String urgency = intent.getPurchaseUrgency() == null ? "" : intent.getPurchaseUrgency();

        // ② 구매 시급성: 오늘 필요 -> 현재 매장에 지금 있고, 그 재고 정보가 confirmed && 신선한 후보만 인정
        // (기능명세서 6번: 미확인/오래된 재고는 확정 구매 경로로 사용하지 않는다)
        if ("TODAY".equalsIgnoreCase(urgency)) {
            List<Candidate> inStockToday = essentialCandidates.stream()
                    .filter(Candidate::isInStockAtCurrentStore)
                    .filter(Candidate::isInventoryReliable)
                    .toList();
            if (!inStockToday.isEmpty()) {
                Candidate best = rankByPreferred(inStockToday, input, essential, preferred, negotiable);
                return DecisionResult.builder()
                        .resultType(ResultType.TODAY_PURCHASE)
                        .recommendedSku(best.getSku())
                        .matchedEssentialKeys(matchedKeys(best.getAttribute(), best.getSku(), essential))
                        .matchedPreferredKeys(matchedKeys(best.getAttribute(), best.getSku(), preferred))
                        .reasonCode("URGENCY_TODAY_MATCH")
                        .build();
            }
            boolean unverifiedStockExists = essentialCandidates.stream()
                    .anyMatch(c -> c.isInStockAtCurrentStore() && !c.isInventoryReliable());
            if (unverifiedStockExists) {
                // 재고가 있다는 값 자체는 있지만 확인/신선도 기준을 통과하지 못함 -> 확정 제안 대신 추가 상담
                return additionalConsultation("URGENCY_TODAY_STOCK_UNVERIFIED");
            }
            // 오늘 필요하지만 현재 매장에 필수 조건을 만족하는 재고가 없다 -> 무리하게 추천하지 않고 추가 상담
            return additionalConsultation("URGENCY_TODAY_NO_STOCK");
        }

        // ③ 실물 확인 필요: 원제품과 같은 확인 요소를 가진 매장 내 후보 제시
        List<String> physicalCheck = intent.getPhysicalCheckAttributes() == null
                ? List.of() : intent.getPhysicalCheckAttributes();
        if (!physicalCheck.isEmpty()) {
            Map<String, String> physicalTargetValues = new LinkedHashMap<>();
            for (String key : physicalCheck) {
                String originalValue = attributeValue(input.getOriginalAttribute(), input.getOriginalSku(), key);
                if (originalValue != null) {
                    physicalTargetValues.put(key, originalValue);
                }
            }
            List<Candidate> physicalMatches = essentialCandidates.stream()
                    .filter(Candidate::isInStockAtCurrentStore)
                    .filter(Candidate::isInventoryReliable)
                    .filter(c -> matchedKeys(c.getAttribute(), c.getSku(), physicalTargetValues).size()
                            == physicalTargetValues.size())
                    .toList();
            if (!physicalMatches.isEmpty()) {
                Candidate best = rankByPreferred(physicalMatches, input, essential, preferred, negotiable);
                return DecisionResult.builder()
                        .resultType(ResultType.COMPARISON_EXPERIENCE)
                        .recommendedSku(best.getSku())
                        .matchedEssentialKeys(matchedKeys(best.getAttribute(), best.getSku(), essential))
                        .matchedPreferredKeys(matchedKeys(best.getAttribute(), best.getSku(), preferred))
                        .reasonCode("PHYSICAL_CHECK_MATCH")
                        .build();
            }
            // 실물 확인 요청은 있었지만 그 속성까지 원제품과 일치하는 후보가 없다 ->
            // 아래 ③-보조 폴백으로 넘어간다 (physicalCheck 값 자체가 essential 밖의 비필수
            // 속성이었을 수 있으므로, essential 만족 후보가 있다면 그걸로 대체 제시한다).
        }
        if (!essential.isEmpty()) {
            // ③-보조: 실물 확인 요청이 없었거나(physicalCheck 비어있음), 있었지만 그 속성까지는
            // 매칭되는 후보가 없었던 경우. 필수 조건 자체는 뚜렷하고 그 조건을 만족하는 다른 SKU 가
            // 이미 매장에 있다면, 원제품의 비필수 속성(색상/소재 등)에는 애초에 집착하지 않았으므로
            // (그래서 essential 이 좁게 잡혔으므로) 굳이 원제품 확보를 기다리게 하기보다 지금 매장에
            // 있는 조건 일치 제품을 우선 제시한다.
            List<Candidate> essentialMatchesInStock = essentialCandidates.stream()
                    .filter(Candidate::isInStockAtCurrentStore)
                    .filter(Candidate::isInventoryReliable)
                    .toList();
            if (!essentialMatchesInStock.isEmpty()) {
                Candidate best = rankByPreferred(essentialMatchesInStock, input, essential, preferred, negotiable);
                return DecisionResult.builder()
                        .resultType(ResultType.COMPARISON_EXPERIENCE)
                        .recommendedSku(best.getSku())
                        .matchedEssentialKeys(matchedKeys(best.getAttribute(), best.getSku(), essential))
                        .matchedPreferredKeys(matchedKeys(best.getAttribute(), best.getSku(), preferred))
                        .reasonCode("ESSENTIAL_MATCH_IN_STORE")
                        .build();
            }
        }

        // ④ 원제품 확보: 타 매장 방문 또는 대기 가능하고, 원제품 자체가 필수 조건을 만족할 때
        // 기능명세서 6번: confirmed && 신선한(reliable) 값만 확정 경로로 인정한다.
        boolean canWait = Boolean.TRUE.equals(intent.getCanWait());
        boolean canVisitOtherStore = Boolean.TRUE.equals(intent.getCanVisitOtherStore());
        boolean otherStoreReliableTrue = input.isOriginalOtherStoreInStock() && input.isOriginalOtherStoreReliable();
        boolean restockReliableTrue = input.isOriginalRestockPlanned() && input.isOriginalRestockReliable();

        if (originalSatisfiesEssential && (canWait || canVisitOtherStore)
                && (otherStoreReliableTrue || restockReliableTrue)) {
            return DecisionResult.builder()
                    .resultType(ResultType.EXACT_PRODUCT)
                    .targetStore(otherStoreReliableTrue ? input.getOriginalOtherStoreLocation() : null)
                    .viaRestock(!otherStoreReliableTrue && restockReliableTrue)
                    .matchedEssentialKeys(originalMatchedKeys)
                    .matchedPreferredKeys(matchedKeys(input.getOriginalAttribute(), input.getOriginalSku(), preferred))
                    .reasonCode(otherStoreReliableTrue ? "WAIT_OK_OTHER_STORE" : "WAIT_OK_RESTOCK")
                    .build();
        }

        boolean unverifiedSecuringPathExists = originalSatisfiesEssential && (canWait || canVisitOtherStore)
                && (input.isOriginalOtherStoreInStock() || input.isOriginalRestockPlanned())
                && !(otherStoreReliableTrue || restockReliableTrue);
        if (unverifiedSecuringPathExists) {
            // 확보 경로 데이터는 있지만 확인/신선도 기준을 통과하지 못함 -> 확정 제안 대신 추가 상담
            return additionalConsultation("INVENTORY_UNVERIFIED");
        }

        // ⑤ 위 조건을 모두 충족하지 못하면 (대기 불가·방문 불가·확보 경로 없음) 추가 상담
        return additionalConsultation("NO_SECURING_PATH");
    }

    private DecisionResult additionalConsultation(String reasonCode) {
        return DecisionResult.builder()
                .resultType(ResultType.ADDITIONAL_CONSULTATION)
                .matchedEssentialKeys(List.of())
                .matchedPreferredKeys(List.of())
                .reasonCode(reasonCode)
                .build();
    }

    /**
     * ⑤ 선호 조건 기준 순위 결정 + 이슈 #3 동점 처리.
     *
     * 여기 들어오는 후보는 모두 필수 조건을 100% 만족한 상태라, 어느 것을 골라도 고객 조건을 어기지
     * 않는다. 그래도 단일 카드 원칙상 하나를 골라야 하므로 아래 순서로 결정한다. 예전에는 선호 조건
     * 개수만 보고 동점이면 먼저 나온 후보를 조용히 골랐는데, 그 선택에는 고객에게 설명할 근거가 없었다.
     *
     * <ol>
     *   <li>선호 조건을 더 많이 만족하는 후보</li>
     *   <li>양보 가능하다고 말한 조건까지 더 많이 지켜주는 후보 (양보를 덜 요구함)</li>
     *   <li>필수 조건을 더 여유 있게 충족하는 후보 (아래 essentialHeadroom 참고)</li>
     *   <li>원제품과 전체 속성이 더 많이 겹치는 후보 ("보시던 제품과 가장 가까운" 근거가 된다)</li>
     *   <li>그래도 같으면 SKU ID 가 작은 후보 (재현 가능한 결정론 보장용)</li>
     * </ol>
     *
     * 이 순위는 필수 조건을 통과한 후보 사이에서만 쓰이며(CLAUDE.md F5 처리 원칙 4), 점수를 만들어
     * 임계값과 비교하지 않는다. 화면에도 숫자로 노출하지 않는다.
     */
    private Candidate rankByPreferred(List<Candidate> candidates, DecisionInput input,
                                       Map<String, String> essential, Map<String, String> preferred,
                                       Map<String, String> negotiable) {
        return candidates.stream()
                .max(Comparator
                        .comparingInt((Candidate c) -> matchedKeys(c.getAttribute(), c.getSku(), preferred).size())
                        .thenComparingInt(c -> matchedKeys(c.getAttribute(), c.getSku(), negotiable).size())
                        .thenComparingInt(c -> essentialHeadroom(c, essential))
                        .thenComparingInt(c -> sharedAttributeCountWithOriginal(c, input))
                        // SKU ID 는 작은 쪽이 이겨야 하므로 max 기준에서는 역순으로 비교한다.
                        .thenComparing(c -> c.getSku().getId(), Comparator.reverseOrder()))
                .orElseThrow();
    }

    /**
     * 필수 조건을 "얼마나 여유 있게" 충족하는지. 동점일 때만 사용한다.
     *
     * 지금은 노트북 수납이 유일한 수용력(capacity) 성격의 조건이다. 고객이 "노트북이 들어가야 해요"
     * 라고만 말하면 essentialConditions 에는 laptopCompatible=true 만 남고 노트북 크기는 알 수 없다.
     * 이때 16인치까지 들어가는 가방은 13인치 가방이 만족시키는 고객을 모두 만족시키지만 그 반대는
     * 성립하지 않으므로, 크기를 모를 때는 여유가 큰 쪽을 제안하는 것이 안전하다.
     *
     * 이 기준이 없으면 외형 근접도(다음 기준)가 먼저 걸려, 원제품과 색이 같다는 이유만으로 수용
     * 범위가 좁은 가방이 선택될 수 있다.
     */
    private int essentialHeadroom(Candidate candidate, Map<String, String> essential) {
        if (!"true".equalsIgnoreCase(essential.get("laptopCompatible"))) {
            return 0;
        }
        Integer maxInch = candidate.getSku() == null ? null : candidate.getSku().getLaptopMaxInch();
        return maxInch == null ? 0 : maxInch;
    }

    /** 후보가 원제품과 값이 같은 속성의 개수. 동점일 때만 사용한다. */
    private int sharedAttributeCountWithOriginal(Candidate candidate, DecisionInput input) {
        int shared = 0;
        for (String key : ALL_ATTRIBUTE_KEYS) {
            String candidateValue = attributeValue(candidate.getAttribute(), candidate.getSku(), key);
            String originalValue = attributeValue(input.getOriginalAttribute(), input.getOriginalSku(), key);
            if (candidateValue != null && originalValue != null
                    && candidateValue.trim().equalsIgnoreCase(originalValue.trim())) {
                shared++;
            }
        }
        return shared;
    }

    private List<String> matchedKeys(ProductAttribute attribute, Sku sku, Map<String, String> conditions) {
        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, String> entry : conditions.entrySet()) {
            String actual = attributeValue(attribute, sku, entry.getKey());
            if (actual != null && actual.trim().equalsIgnoreCase(entry.getValue().trim())) {
                matched.add(entry.getKey());
            }
        }
        return matched;
    }

    private String attributeValue(ProductAttribute a, Sku sku, String key) {
        if (a == null && sku == null) {
            return null;
        }
        return switch (key) {
            case "colorFamily" -> a == null ? null : a.getColorFamily();
            case "colorTone" -> a == null ? null : a.getColorTone();
            case "material" -> a == null ? null : a.getMaterial();
            case "glossLevel" -> a == null ? null : a.getGlossLevel();
            case "logoVisibility" -> a == null ? null : a.getLogoVisibility();
            case "logoPosition" -> a == null ? null : a.getLogoPosition();
            case "patternDensity" -> a == null ? null : a.getPatternDensity();
            case "silhouette" -> a == null ? null : a.getSilhouette();
            case "structure" -> a == null ? null : a.getStructure();
            case "sizeGrade" -> a == null ? null : a.getSizeGrade();
            case "strapType" -> a == null ? null : a.getStrapType();
            case "hardwareColor" -> a == null ? null : a.getHardwareColor();
            case "usageContext" -> a == null ? null : a.getUsageContext();
            case "weightGrade" -> a == null ? null : a.getWeightGrade();
            case "lockType" -> a == null ? null : a.getLockType();
            case "internalStorageLevel" -> a == null ? null : a.getInternalStorageLevel();
            case "laptopCompatible" -> sku == null || sku.getLaptopCompatible() == null
                    ? null : String.valueOf(sku.getLaptopCompatible());
            case "laptopMaxInch" -> sku == null || sku.getLaptopMaxInch() == null
                    ? null : String.valueOf(sku.getLaptopMaxInch());
            case "handleType" -> a == null ? null : a.getHandleType();
            default -> null;
        };
    }

    private Map<String, String> safeMap(Map<String, String> map) {
        return map == null ? Map.of() : map;
    }
}
