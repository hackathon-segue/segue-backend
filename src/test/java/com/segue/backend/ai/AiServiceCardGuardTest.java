package com.segue.backend.ai;

import com.segue.backend.ai.dto.LastIntentCardDto;
import com.segue.backend.domain.Product;
import com.segue.backend.domain.Sku;
import com.segue.backend.domain.enums.ResultType;
import com.segue.backend.dto.StructuredIntentDto;
import com.segue.backend.engine.DecisionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이슈 #34: 고객이 조건을 하나도 말하지 않았는데 카드가 조건이 존재하는 것처럼 서술하는 문제.
 *
 * card.txt 프롬프트로 1차 방지하지만 프롬프트만으로는 100% 막지 못하므로(금지어 때와 동일),
 * AI 가 계속 조건을 지어내는 상황을 실제로 만들어 백엔드 2차 방어가 동작하는지 검증한다.
 * OpenAI 를 실제로 호출하지 않고 OpenAiClient 를 스텁으로 대체한다.
 */
class AiServiceCardGuardTest {

    /** 조건을 지어내는 응답을 항상 돌려주는 스텁. 호출 횟수도 센다. */
    private static class HallucinatingClient extends OpenAiClient {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String callJson(String systemPrompt, String userMessage) {
            calls.incrementAndGet();
            return """
                    {
                      "coreConditions": "고객님께서는 특정 속성을 반드시 유지해야 하며, 구매 시급성에 대한 요구는 없으십니다.",
                      "nextAction": "추가 상담을 통해 요구 사항을 확인하겠습니다.",
                      "reason": "확정된 경로가 없어 추가 상담이 필요합니다.",
                      "difference": "구체적인 제품이나 경로가 정해지지 않았습니다."
                    }
                    """;
        }
    }

    private LastIntentCardDto generateWithEmptyIntent(OpenAiClient client) {
        AiService aiService = new AiService(client, new PromptLoader());

        StructuredIntentDto emptyIntent = new StructuredIntentDto();
        emptyIntent.setEssentialConditions(Map.of());
        emptyIntent.setPreferredConditions(Map.of());
        emptyIntent.setPhysicalCheckAttributes(List.of());

        DecisionResult decision = DecisionResult.builder()
                .resultType(ResultType.ADDITIONAL_CONSULTATION)
                .matchedEssentialKeys(List.of())
                .matchedPreferredKeys(List.of())
                .reasonCode("NO_SECURING_PATH")
                .build();

        Product product = Product.builder().name("M Diamond 비세토스 레더 믹스").build();
        Sku originalSku = Sku.builder().product(product).color("꼬냑").size("M").build();

        return aiService.generateCard(decision, emptyIntent, originalSku, null, null, null, "추가 상담 필요");
    }

    @Test
    void 조건이_없는데_AI가_조건을_지어내면_규칙_기반_문구로_대체한다() {
        HallucinatingClient client = new HallucinatingClient();

        LastIntentCardDto card = generateWithEmptyIntent(client);

        // 1회 재시도까지 시도한 뒤 폴백해야 한다 (최초 1회 + 재시도 1회)
        assertThat(client.calls.get()).isEqualTo(2);
        // 폴백 문구 자체도 단정 표현을 포함하지 않아야 한다 (자기 검사를 통과해야 함)
        assertThat(card.getCoreConditions())
                .doesNotContain("반드시")
                .doesNotContain("필수")
                .doesNotContain("꼭")
                .doesNotContain("절대")
                .isEqualTo("아직 고객님께서 중요하게 보시는 조건은 확인되지 않았습니다.");
        // 나머지 문구는 AI 응답을 그대로 유지한다 (문제가 있는 항목만 교체)
        assertThat(card.getNextAction()).contains("추가 상담");
    }

    @Test
    void 재시도에서_정상_문구가_오면_그대로_사용한다() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public String callJson(String systemPrompt, String userMessage) {
                if (calls.incrementAndGet() == 1) {
                    return """
                            {"coreConditions":"고객님께서는 특정 속성을 반드시 유지해야 하십니다.",
                             "nextAction":"a","reason":"b","difference":"c"}
                            """;
                }
                return """
                        {"coreConditions":"아직 중요하게 보시는 조건은 확인되지 않았습니다.",
                         "nextAction":"a","reason":"b","difference":"c"}
                        """;
            }
        };

        LastIntentCardDto card = generateWithEmptyIntent(client);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(card.getCoreConditions()).isEqualTo("아직 중요하게 보시는 조건은 확인되지 않았습니다.");
    }

    @Test
    void 조건이_실제로_있으면_단정_표현을_막지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public String callJson(String systemPrompt, String userMessage) {
                calls.incrementAndGet();
                return """
                        {"coreConditions":"고객님께서는 노트북 수납을 반드시 유지하고자 하십니다.",
                         "nextAction":"a","reason":"b","difference":"c"}
                        """;
            }
        };

        AiService aiService = new AiService(client, new PromptLoader());

        StructuredIntentDto intent = new StructuredIntentDto();
        intent.setEssentialConditions(Map.of("laptopCompatible", "true"));
        intent.setPreferredConditions(Map.of());
        intent.setPhysicalCheckAttributes(List.of());

        DecisionResult decision = DecisionResult.builder()
                .resultType(ResultType.TODAY_PURCHASE)
                .matchedEssentialKeys(List.of("laptopCompatible"))
                .matchedPreferredKeys(List.of())
                .reasonCode("URGENCY_TODAY_MATCH")
                .build();

        Product product = Product.builder().name("원제품").build();
        Sku sku = Sku.builder().product(product).color("꼬냑").size("M").build();

        LastIntentCardDto card = aiService.generateCard(
                decision, intent, sku, null, null, null, "현재 매장 재고 확인");

        // 조건이 실제로 있으므로 재시도 없이 1회 호출로 끝나고 문구도 그대로 유지된다
        assertThat(calls.get()).isEqualTo(1);
        assertThat(card.getCoreConditions()).contains("반드시");
    }
}
