package com.segue.backend.exception;

/** 이메일·전화번호 중복처럼 이미 존재하는 값과 충돌하는 경우. 409 로 변환된다. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
