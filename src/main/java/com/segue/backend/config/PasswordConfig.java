package com.segue.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해싱 설정.
 *
 * spring-boot-starter-security 대신 spring-security-crypto 만 의존한다. 스타터를 넣으면
 * 자동 설정이 모든 엔드포인트에 기본 인증을 걸어 현재 동작하는 API 가 전부 401 이 되므로,
 * 해싱 기능만 필요한 이 프로젝트에는 crypto 모듈만 쓰는 것이 맞다.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
