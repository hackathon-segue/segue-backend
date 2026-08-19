package com.segue.backend.ai;

import com.segue.backend.ai.dto.FollowUpQuestionDto;
import com.segue.backend.ai.dto.LastIntentCardDto;
import com.segue.backend.domain.ProductAttribute;
import com.segue.backend.domain.Sku;
import com.segue.backend.dto.StructuredIntentDto;
import com.segue.backend.engine.DecisionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 백엔드가 호출하는 AI 서비스 모듈 (segue-ai 파트).
 * intent.txt / followup.txt / card.txt 3개 프롬프트를 감싸는 유일한 진입점이며,
 * 컨트롤러/서비스 계층은 OpenAiClient 를 직접 호출하지 않고 이 클래스만 사용한다.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    /**
     * F6 카드 생성문에 절대 들어가면 안 되는 표현 (CLAUDE.md 금지어).
     * card.txt 프롬프트로 1차 방지하지만, 실제 호출에서 "대체 제품" 처럼 변형된 형태로
     * 새어나오는 것을 확인해 백엔드에서 2차로 검증한다 (부분 문자열 포함 여부 기준).
     */
    private static final List<String> BANNED_SUBSTRINGS = List.of("품절", "대체", "BEST MATCH", "적합도");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("\\d+\\s*%");

    /**
     * 고객이 필수 조건을 말한 적이 없을 때(= essentialConditions 가 비어 있을 때) 카드 문구에 나타나면
     * 안 되는 단정 표현. 실제 호출에서 조건이 하나도 없는데도 "고객님께서는 특정 속성을 반드시
     * 유지해야 하며..." 같은 문장이 생성되는 것을 확인했다(이슈 #34).
     * 금지어 필터는 문자열 단위라 이런 내용 환각은 잡지 못한다.
     */
    private static final List<String> ASSERTIVE_CONDITION_WORDS = List.of("반드시", "필수", "꼭", "절대");

    /**
     * 위 상황에서 AI 재시도까지 실패했을 때 쓰는 규칙 기반 문구. 없는 조건을 지어내지 않는다.
     * 이 문구 자체도 ASSERTIVE_CONDITION_WORDS 를 포함하지 않아야 한다 (자기 검사를 통과해야 함).
     */
    private static final String NO_CONDITION_FALLBACK =
            "아직 고객님께서 중요하게 보시는 조건은 확인되지 않았습니다.";

    private final OpenAiClient openAiClient;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** F3: 고객 자연어 발화 → 구조화된 의도 */
    public StructuredIntentDto structureIntent(String customerUtterance) {
        String systemPrompt = promptLoader.load("intent.txt");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("conversation", customerUtterance);
        return callAndParse(systemPrompt, payload, StructuredIntentDto.class);
    }

    /** F4: 보충 질문에 대한 답변까지 반영해 의도를 다시 정리 */
    public StructuredIntentDto restructureWithFollowUp(String originalUtterance,
                                                         String followUpQuestion,
                                                         String followUpAnswer) {
        String systemPrompt = promptLoader.load("intent.txt");
        String combined = "[최초 발화]\n" + originalUtterance
                + "\n\n[보충 질문]\n" + followUpQuestion
                + "\n\n[보충 답변]\n" + followUpAnswer;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("conversation", combined);
        return callAndParse(systemPrompt, payload, StructuredIntentDto.class);
    }

    /** F4: 필수 조건/구매 상황이 불충분할 때 CA에게 보여줄 보충 질문 1개 생성 */
    public String generateFollowUpQuestion(String customerUtterance, StructuredIntentDto currentIntent) {
        String systemPrompt = promptLoader.load("followup.txt");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("conversation", customerUtterance);
        payload.set("currentIntent", objectMapper.valueToTree(currentIntent));
        FollowUpQuestionDto result = callAndParse(systemPrompt, payload, FollowUpQuestionDto.class);
        return result.getQuestion();
    }

    /** F6: 규칙 엔진이 확정한 결과를 브랜드 톤에 맞춰 4가지 요소로 설명 */
    public LastIntentCardDto generateCard(DecisionResult decision,
                                           StructuredIntentDto intent,
                                           Sku originalSku,
                                           ProductAttribute originalAttribute,
                                           Sku recommendedSku,
                                           ProductAttribute recommendedAttribute,
                                           String pathDescription) {
        String systemPrompt = promptLoader.load("card.txt");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("resultType", decision.getResultType().name());
        payload.put("reasonCode", decision.getReasonCode());
        payload.set("customerIntent", objectMapper.valueToTree(intent));
        payload.set("matchedEssentialKeys", objectMapper.valueToTree(decision.getMatchedEssentialKeys()));
        payload.set("matchedPreferredKeys", objectMapper.valueToTree(decision.getMatchedPreferredKeys()));

        ObjectNode originalNode = objectMapper.createObjectNode();
        originalNode.put("productName", originalSku.getProduct().getName());
        originalNode.put("color", originalSku.getColor());
        originalNode.put("size", originalSku.getSize());
        if (originalAttribute != null) {
            originalNode.set("attributes", objectMapper.valueToTree(originalAttribute));
        }
        payload.set("originalProduct", originalNode);

        if (recommendedSku != null) {
            ObjectNode recommendedNode = objectMapper.createObjectNode();
            recommendedNode.put("productName", recommendedSku.getProduct().getName());
            recommendedNode.put("color", recommendedSku.getColor());
            recommendedNode.put("size", recommendedSku.getSize());
            if (recommendedAttribute != null) {
                recommendedNode.set("attributes", objectMapper.valueToTree(recommendedAttribute));
            }
            payload.set("recommendedProduct", recommendedNode);
        }
        payload.put("pathDescription", pathDescription);

        boolean noStatedConditions = hasNoStatedConditions(intent);

        LastIntentCardDto card = callAndParse(systemPrompt, payload, LastIntentCardDto.class);
        if (containsBannedLanguage(card) || inventsConditions(card, noStatedConditions)) {
            // 1회 재시도 (AI가 매번 같은 실수를 반복하지 않는 경우가 많음)
            card = callAndParse(systemPrompt, payload, LastIntentCardDto.class);
            if (containsBannedLanguage(card)) {
                throw new IllegalStateException(
                        "AI가 생성한 카드 문구에 금지어가 포함되어 있어 안전하게 실패 처리합니다.");
            }
            if (inventsConditions(card, noStatedConditions)) {
                // 금지어와 달리 이 경우는 안전하게 대신 쓸 수 있는 사실 기반 문장이 있으므로,
                // 상담 전체를 실패시키지 않고 해당 문구만 규칙 기반 문장으로 바꾼다.
                card.setCoreConditions(NO_CONDITION_FALLBACK);
            }
        }
        return card;
    }

    /** 고객이 필수/선호/실물확인 어느 것도 말하지 않은 상태인지 (= 서술할 조건이 실제로 없음) */
    private boolean hasNoStatedConditions(StructuredIntentDto intent) {
        return isEmpty(intent.getEssentialConditions())
                && isEmpty(intent.getPreferredConditions())
                && (intent.getPhysicalCheckAttributes() == null
                    || intent.getPhysicalCheckAttributes().isEmpty());
    }

    private boolean isEmpty(java.util.Map<String, String> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 말한 조건이 하나도 없는데 카드가 조건이 존재하는 것처럼 단정하는지 검사한다.
     * 조건이 실제로 있으면 "반드시" 같은 표현은 정상이므로 검사 대상이 아니다.
     */
    private boolean inventsConditions(LastIntentCardDto card, boolean noStatedConditions) {
        if (!noStatedConditions) {
            return false;
        }
        String coreConditions = nullToEmpty(card.getCoreConditions());
        return ASSERTIVE_CONDITION_WORDS.stream().anyMatch(coreConditions::contains);
    }

    private boolean containsBannedLanguage(LastIntentCardDto card) {
        String combined = String.join(" ",
                nullToEmpty(card.getCoreConditions()), nullToEmpty(card.getNextAction()),
                nullToEmpty(card.getReason()), nullToEmpty(card.getDifference()));
        for (String banned : BANNED_SUBSTRINGS) {
            if (combined.contains(banned)) {
                return true;
            }
        }
        return PERCENT_PATTERN.matcher(combined).find();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> T callAndParse(String systemPrompt, ObjectNode payload, Class<T> type) {
        String responseJson = openAiClient.callJson(systemPrompt, payload.toString());
        try {
            return objectMapper.readValue(responseJson, type);
        } catch (Exception firstParseError) {
            // Issue #16: JSON 파싱 실패 시 OpenAI 1회 재호출 후 다시 파싱 시도
            String retryJson = openAiClient.callJson(systemPrompt, payload.toString());
            try {
                return objectMapper.readValue(retryJson, type);
            } catch (Exception secondParseError) {
                throw new IllegalStateException("AI 응답을 파싱하지 못했습니다 (재시도 포함): " + retryJson, secondParseError);
            }
        }
    }
}
