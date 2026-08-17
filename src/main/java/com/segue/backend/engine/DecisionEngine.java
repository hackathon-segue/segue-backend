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

    public DecisionResult decide(DecisionInput input) {
        StructuredIntentDto intent = input.getIntent();
        Map<String, String> essential = safeMap(intent.getEssentialConditions());
        Map<String, String> preferred = safeMap(intent.getPreferredConditions());

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
                Candidate best = rankByPreferred(inStockToday, preferred);
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
                Candidate best = rankByPreferred(physicalMatches, preferred);
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
                Candidate best = rankByPreferred(essentialMatchesInStock, preferred);
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

    private Candidate rankByPreferred(List<Candidate> candidates, Map<String, String> preferred) {
        return candidates.stream()
                .max(Comparator.comparingInt(c -> matchedKeys(c.getAttribute(), c.getSku(), preferred).size()))
                .orElseThrow();
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
