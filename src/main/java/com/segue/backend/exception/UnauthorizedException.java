package com.segue.backend.exception;

/** 로그인 실패 등 인증 실패. 401 로 변환된다. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
