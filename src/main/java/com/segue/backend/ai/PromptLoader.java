package com.segue.backend.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** src/main/resources/prompts/*.txt 프롬프트 파일을 읽어온다. */
@Component
public class PromptLoader {

    public String load(String fileName) {
        ClassPathResource resource = new ClassPathResource("prompts/" + fileName);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일을 읽을 수 없습니다: " + fileName, e);
        }
    }
}
