package com.segue.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI Chat Completions API 를 JSON mode 로 호출하는 저수준 클라이언트.
 * gpt-4o-mini 고정, response_format=json_object 로 항상 구조화된 JSON 문자열을 받는다.
 */
@Component
public class OpenAiClient {

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    /**
     * @param systemPrompt prompts/*.txt 의 내용 (system 역할)
     * @param userMessage  실제 상담 컨텍스트를 담은 JSON 문자열 (user 역할)
     * @return 모델이 반환한 JSON 문자열 (message.content)
     */
    public String callJson(String systemPrompt, String userMessage) {
        String requestBody = buildRequestBody(systemPrompt, userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("OpenAI API 호출 중 오류가 발생했습니다.", e);
        }

        if (response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI API 호출 실패 (status=" + response.statusCode()
                    + "): " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.at("/choices/0/message/content").asString();
    }

    private String buildRequestBody(String systemPrompt, String userMessage) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);

        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_object");
        body.set("response_format", responseFormat);

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userMessage));
        body.set("messages", messages);

        body.put("temperature", 0.3);

        return objectMapper.writeValueAsString(body);
    }

    private ObjectNode message(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }
}
