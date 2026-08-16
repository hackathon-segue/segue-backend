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

/**
 * 백엔드가 호출하는 AI 서비스 모듈 (segue-ai 파트).
 * intent.txt / followup.txt / card.txt 3개 프롬프트를 감싸는 유일한 진입점이며,
 * 컨트롤러/서비스 계층은 OpenAiClient 를 직접 호출하지 않고 이 클래스만 사용한다.
 */
@Service
@RequiredArgsConstructor
public class AiService {

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

        return callAndParse(systemPrompt, payload, LastIntentCardDto.class);
    }

    private <T> T callAndParse(String systemPrompt, ObjectNode payload, Class<T> type) {
        String responseJson = openAiClient.callJson(systemPrompt, payload.toString());
        try {
            return objectMapper.readValue(responseJson, type);
        } catch (Exception e) {
            throw new IllegalStateException("AI 응답을 파싱하지 못했습니다: " + responseJson, e);
        }
    }
}
