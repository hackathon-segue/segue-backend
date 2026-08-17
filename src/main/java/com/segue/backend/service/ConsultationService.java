package com.segue.backend.service;

import com.segue.backend.ai.AiService;
import com.segue.backend.ai.dto.LastIntentCardDto;
import com.segue.backend.domain.*;
import com.segue.backend.domain.enums.ActionType;
import com.segue.backend.domain.enums.ResultType;
import com.segue.backend.dto.StructuredIntentDto;
import com.segue.backend.dto.request.*;
import com.segue.backend.dto.response.*;
import com.segue.backend.engine.Candidate;
import com.segue.backend.engine.DecisionEngine;
import com.segue.backend.engine.DecisionInput;
import com.segue.backend.engine.DecisionResult;
import com.segue.backend.domain.enums.ExecutionStatus;
import com.segue.backend.exception.NotFoundException;
import com.segue.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** F3~F8 상담 플로우를 조율한다. */
@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final AiService aiService;
    private final DecisionEngine decisionEngine;

    private final SkuRepository skuRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final InventoryRepository inventoryRepository;
    private final StoreRepository storeRepository;
    private final CustomerRepository customerRepository;
    private final ConsultationResultRepository consultationResultRepository;
    private final CustomerService customerService;

    /**
     * 기능명세서 6번: 재고 정보의 "최신성 기준" 시간(시간 단위). 이 시간을 넘긴 confirmed 재고는
     * 미확인과 동일하게 취급한다. 구체적인 수치는 팀 테스트 후 조정 대상 (기본 12시간).
     */
    @Value("${inventory.freshness-hours:12}")
    private long freshnessHours;

    // ---------- F3 ----------
    @Transactional(readOnly = true)
    public IntentStructureResponse structureIntent(IntentStructureRequest request) {
        requireSku(request.getSkuId());
        StructuredIntentDto intent = aiService.structureIntent(request.getUtterance());
        return IntentStructureResponse.builder()
                .structuredIntent(intent)
                .needsFollowUp(intent.isNeedsFollowUp())
                .build();
    }

    // ---------- F4 (1/2): 보충 질문 생성 ----------
    public FollowUpQuestionResponse generateFollowUpQuestion(FollowUpQuestionRequest request) {
        String question = aiService.generateFollowUpQuestion(request.getUtterance(), request.getCurrentIntent());
        return FollowUpQuestionResponse.builder().question(question).build();
    }

    // ---------- F4 (2/2): 보충 답변 반영해 재구조화 (최대 1회이므로 이후 needsFollowUp 은 강제로 false) ----------
    public IntentStructureResponse restructureWithFollowUp(FollowUpAnswerRequest request) {
        StructuredIntentDto intent = aiService.restructureWithFollowUp(
                request.getUtterance(), request.getFollowUpQuestion(), request.getFollowUpAnswer());
        intent.setNeedsFollowUp(false);
        return IntentStructureResponse.builder()
                .structuredIntent(intent)
                .needsFollowUp(false)
                .build();
    }

    // ---------- F5 + F6 ----------
    @Transactional(readOnly = true)
    public LastIntentCardResponse decide(DecideRequest request) {
        Sku originalSku = requireSku(request.getSkuId());
        ProductAttribute originalAttribute = productAttributeRepository.findBySkuId(originalSku.getId())
                .orElse(null);

        Inventory originalInventoryAtCurrentStore = inventoryRepository
                .findBySkuIdAndStoreId(originalSku.getId(), request.getStoreId())
                .orElseThrow(() -> new NotFoundException("현재 매장의 재고 정보를 찾을 수 없습니다."));

        Store otherStoreLocation = inventoryRepository.findBySkuId(originalSku.getId()).stream()
                .filter(inv -> !inv.getStore().getId().equals(request.getStoreId()))
                .filter(Inventory::getCurrentStoreInStock)
                .map(Inventory::getStore)
                .findFirst()
                .orElse(null);

        List<Candidate> candidates = skuRepository.findAll().stream()
                .filter(sku -> !sku.getId().equals(originalSku.getId()))
                .map(sku -> {
                    Optional<Inventory> inv = inventoryRepository
                            .findBySkuIdAndStoreId(sku.getId(), request.getStoreId());
                    return Candidate.builder()
                            .sku(sku)
                            .attribute(productAttributeRepository.findBySkuId(sku.getId()).orElse(null))
                            .inStockAtCurrentStore(inv.map(Inventory::getCurrentStoreInStock).orElse(false))
                            .inventoryReliable(inv.map(this::isReliable).orElse(false))
                            .build();
                })
                .toList();

        DecisionInput input = DecisionInput.builder()
                .originalSku(originalSku)
                .originalAttribute(originalAttribute)
                .originalOtherStoreInStock(Boolean.TRUE.equals(originalInventoryAtCurrentStore.getOtherStoreInStock()))
                .originalOtherStoreLocation(otherStoreLocation)
                .originalRestockPlanned(Boolean.TRUE.equals(originalInventoryAtCurrentStore.getRestockPlanned()))
                .originalOtherStoreReliable(isReliable(originalInventoryAtCurrentStore))
                .originalRestockReliable(isReliable(originalInventoryAtCurrentStore))
                .intent(request.getStructuredIntent())
                .candidates(candidates)
                .build();

        DecisionResult decision = decisionEngine.decide(input);

        ProductAttribute recommendedAttribute = decision.getRecommendedSku() == null
                ? null
                : productAttributeRepository.findBySkuId(decision.getRecommendedSku().getId()).orElse(null);

        String pathDescription = buildPathDescription(decision);

        LastIntentCardDto card = aiService.generateCard(
                decision, request.getStructuredIntent(), originalSku, originalAttribute,
                decision.getRecommendedSku(), recommendedAttribute, pathDescription);

        ActionType actionType = resolveActionType(decision);

        return LastIntentCardResponse.builder()
                .resultType(decision.getResultType())
                .coreConditions(card.getCoreConditions())
                .nextAction(card.getNextAction())
                .reason(card.getReason())
                .difference(card.getDifference())
                .recommendedProduct(decision.getRecommendedSku() == null
                        ? null : ProductSummaryResponse.from(decision.getRecommendedSku()))
                .pathDescription(pathDescription)
                .actionType(actionType)
                .actionButtonLabel(actionButtonLabel(actionType))
                .build();
    }

    // ---------- F7 + F8 ----------
    @Transactional
    public ExecuteResponse execute(ExecuteRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new NotFoundException("고객을 찾을 수 없습니다. id=" + request.getCustomerId()));
        // 기능명세서 5번: 동의하지 않은 고객의 상담 결과는 고객 정보에 저장하지 않는다.
        customerService.requireConsent(request.getCustomerId());
        Sku originalSku = requireSku(request.getSkuId());

        String recommendedPath = request.getPathDescription();
        if (request.getRecommendedSkuId() != null) {
            Sku recommendedSku = requireSku(request.getRecommendedSkuId());
            recommendedPath = recommendedSku.getProduct().getName()
                    + " (" + recommendedSku.getColor() + "/" + recommendedSku.getSize() + ") - "
                    + request.getPathDescription();
        }

        LocalDateTime now = LocalDateTime.now();
        ConsultationResult saved = consultationResultRepository.save(ConsultationResult.builder()
                .customer(customer)
                .sku(originalSku)
                .resultType(request.getResultType())
                .recommendedPath(recommendedPath)
                .coreConditions(request.getCoreConditionsSummary())
                .consultedAt(now)
                // 기능명세서 7번: 실행 버튼 시점의 기본 처리 상태는 "요청 접수"이다.
                .executionStatus(ExecutionStatus.REQUESTED)
                .executionUpdatedAt(now)
                .build());

        return ExecuteResponse.builder()
                .consultationResultId(saved.getId())
                .completionMessage(completionMessage(request.getActionType()))
                .build();
    }

    /** 기능명세서 7번: CA가 실행 결과를 실행 불가/후속 확인 필요 등으로 갱신한다 (중복 기록 없이 같은 행을 갱신). */
    @Transactional
    public ConsultationResultResponse updateExecutionStatus(Long consultationResultId,
                                                              ExecutionStatusUpdateRequest request) {
        ConsultationResult result = consultationResultRepository.findById(consultationResultId)
                .orElseThrow(() -> new NotFoundException("상담 결과를 찾을 수 없습니다. id=" + consultationResultId));

        boolean requiresNote = request.getStatus() == ExecutionStatus.UNABLE
                || request.getStatus() == ExecutionStatus.FOLLOW_UP_NEEDED;
        if (requiresNote && (request.getNote() == null || request.getNote().isBlank())) {
            throw new IllegalArgumentException("실행 불가 또는 후속 확인 필요 상태는 사유/안내(note) 없이 처리할 수 없습니다.");
        }

        result.setExecutionStatus(request.getStatus());
        result.setExecutionNote(request.getNote());
        result.setExecutionUpdatedAt(LocalDateTime.now());
        return ConsultationResultResponse.from(result);
    }

    @Transactional(readOnly = true)
    public Page<ConsultationResultResponse> getResultsForCustomer(Long customerId, Pageable pageable) {
        // 기능명세서 5번: 동의한 경우에만 고객 모바일에서 상담 결과를 재확인할 수 있다.
        customerService.requireConsent(customerId);
        return consultationResultRepository.findByCustomerIdOrderByConsultedAtDesc(customerId, pageable)
                .map(ConsultationResultResponse::from);
    }

    // ---------- helpers ----------
    private boolean isReliable(Inventory inventory) {
        return Boolean.TRUE.equals(inventory.getConfirmed())
                && inventory.getCheckedAt() != null
                && inventory.getCheckedAt().isAfter(LocalDateTime.now().minusHours(freshnessHours));
    }

    private Sku requireSku(Long skuId) {
        return skuRepository.findById(skuId)
                .orElseThrow(() -> new NotFoundException("SKU를 찾을 수 없습니다. id=" + skuId));
    }

    private String buildPathDescription(DecisionResult decision) {
        return switch (decision.getResultType()) {
            case EXACT_PRODUCT -> decision.isViaRestock()
                    ? "입고 예정 확인"
                    : Optional.ofNullable(decision.getTargetStore()).map(Store::getName).orElse("타 매장") + " 재고 확인";
            case COMPARISON_EXPERIENCE -> "현재 매장 비교 체험 제품 확인";
            case TODAY_PURCHASE -> "현재 매장 재고 확인";
            case ADDITIONAL_CONSULTATION -> "추가 상담 필요";
        };
    }

    private ActionType resolveActionType(DecisionResult decision) {
        return switch (decision.getResultType()) {
            case EXACT_PRODUCT -> decision.isViaRestock()
                    ? ActionType.RESTOCK_CHECK_REQUEST : ActionType.OTHER_STORE_CHECK_REQUEST;
            case COMPARISON_EXPERIENCE, TODAY_PURCHASE -> ActionType.PRODUCT_CHECK_REQUEST;
            case ADDITIONAL_CONSULTATION -> ActionType.RECONSULT;
        };
    }

    private String actionButtonLabel(ActionType actionType) {
        return switch (actionType) {
            case OTHER_STORE_CHECK_REQUEST -> "타 매장 확인 요청";
            case RESTOCK_CHECK_REQUEST -> "입고 확인 신청";
            case PRODUCT_CHECK_REQUEST -> "이 제품 확인하기";
            case RECONSULT -> "조건 다시 확인하기";
        };
    }

    private String completionMessage(ActionType actionType) {
        return switch (actionType) {
            case OTHER_STORE_CHECK_REQUEST -> "요청이 접수되었습니다. CA가 실제 재고를 확인합니다";
            case RESTOCK_CHECK_REQUEST -> "확인 신청이 접수되었습니다";
            case PRODUCT_CHECK_REQUEST -> "CA에게 제품 확인을 요청했습니다";
            case RECONSULT -> "고객의 조건을 다시 확인합니다";
        };
    }
}
