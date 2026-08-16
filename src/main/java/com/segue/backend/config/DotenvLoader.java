package com.segue.backend.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 프로젝트 루트(.env)의 KEY=VALUE 값을 System property로 등록한다.
 * Spring Context 가 뜨기 전, main() 에서 가장 먼저 호출해야 application.properties 의
 * ${DB_HOST} 같은 플레이스홀더가 정상적으로 치환된다.
 *
 * 이미 설정된 실제 환경변수/시스템 프로퍼티가 있으면 .env 값으로 덮어쓰지 않는다
 * (배포 환경에서는 실제 환경변수가 우선하고, 로컬 개발에서만 .env 가 쓰이도록).
 */
public final class DotenvLoader {

    private DotenvLoader() {
    }

    public static void load() {
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separatorIndex = trimmed.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separatorIndex).trim();
                String value = unquote(trimmed.substring(separatorIndex + 1).trim());
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(".env 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }

    private static String unquote(String value) {
        boolean wrapped = value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")));
        return wrapped ? value.substring(1, value.length() - 1) : value;
    }
}
