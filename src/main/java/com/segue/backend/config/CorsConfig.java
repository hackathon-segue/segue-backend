package com.segue.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 고객 모바일 웹 / 직원 웹에서 이 백엔드를 호출할 수 있도록 CORS 를 허용한다.
 *
 * 허용 오리진은 환경별로 다르다. 개발은 프론트 개발 서버 포트가 매번 바뀌므로 전체를 허용하고,
 * 운영은 application-prod.properties 에서 실제 서비스 주소만 지정한다. 프론트를 백엔드와 같은
 * 주소에서 서빙하면 CORS 자체가 발생하지 않으므로 이 설정은 그때 무의미해진다(그래도 남겨둔다).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String[] ALLOWED_METHODS =
            {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"};

    /** 쉼표로 구분한 허용 오리진 패턴. 기본값은 개발 편의를 위한 전체 허용. */
    @Value("${cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods(ALLOWED_METHODS)
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);

        // 제품 이미지(static/images/products/*.png)도 CORS 대상이다.
        // Flutter Web 의 CanvasKit 렌더러는 <img> 태그가 아니라 fetch 로 이미지를 가져와 캔버스에
        // 그리므로, 일반 이미지 로드와 달리 Access-Control-Allow-Origin 헤더가 없으면 차단된다.
        registry.addMapping("/images/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
