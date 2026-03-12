package com.boaz.backend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "요청 파라미터가 누락되었습니다."),

    // Recruitment
    RECRUITMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECRUITMENT_NOT_FOUND", "해당 모집 공고를 찾을 수 없습니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "ALREADY_APPLIED", "이미 지원한 모집 공고입니다."),
    RECRUITMENT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "RECRUITMENT_NOT_AVAILABLE", "현재 지원 가능한 기간이 아닙니다."),
    INVALID_TRACK_NAME(HttpStatus.BAD_REQUEST, "INVALID_TRACK_NAME", "올바른 지원 부문을 선택해주세요."),
    QUESTIONS_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "QUESTIONS_NOT_FOUND", "등록된 질문이 없습니다."),

    // Curriculum
    CURRICULUM_NOT_FOUND(HttpStatus.NOT_FOUND, "CURRICULUM_NOT_FOUND", "해당 커리큘럼을 찾을 수 없습니다."),

    // Review
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "해당 후기를 찾을 수 없습니다."),

    // FAQ
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND, "FAQ_NOT_FOUND", "해당 FAQ를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
