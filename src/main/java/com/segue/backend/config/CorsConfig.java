package com.segue.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Flutter Web(고객 모바일 + 태블릿)에서 이 백엔드를 호출할 수 있도록 CORS 를 허용한다. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String[] ALLOWED_METHODS =
            {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"};

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods(ALLOWED_METHODS)
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);

        // 제품 이미지(static/images/products/*.png)도 CORS 대상이다.
        // Flutter Web 의 CanvasKit 렌더러는 <img> 태그가 아니라 fetch 로 이미지를 가져와 캔버스에
        // 그리므로, 일반 이미지 로드와 달리 Access-Control-Allow-Origin 헤더가 없으면 차단된다.
        // 프론트 개발 서버는 실행할 때마다 포트가 바뀌므로 오리진은 패턴으로 허용한다.
        registry.addMapping("/images/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
