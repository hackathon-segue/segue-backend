package com.segue.backend.exception;

/** 기능명세서 5번: 동의하지 않은(또는 아직 동의 기록이 없는) 고객의 데이터 조회·저장을 차단할 때 던진다. */
public class ConsentRequiredException extends RuntimeException {
    public ConsentRequiredException(String message) {
        super(message);
    }
}
