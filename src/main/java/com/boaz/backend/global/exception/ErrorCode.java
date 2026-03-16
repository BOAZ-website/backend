package com.boaz.backend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "요청 파라미터가 누락되었습니다."),
    INVALID_PARAMETER_TYPE(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER_TYPE", "파라미터 타입이 올바르지 않습니다."),

    // Recruitment
    RECRUITMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECRUITMENT_NOT_FOUND", "해당 모집 공고를 찾을 수 없습니다."),
    RECRUITMENT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "RECRUITMENT_NOT_AVAILABLE", "현재 지원 가능한 기간이 아닙니다."),
    QUESTIONS_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTIONS_NOT_FOUND", "등록된 질문이 없습니다."),
    RECRUITMENT_CLOSED(HttpStatus.BAD_REQUEST, "RECRUITMENT_CLOSED", "현재 지원 기간이 아닙니다."),
    ANSWER_REQUIRED(HttpStatus.BAD_REQUEST, "ANSWER_REQUIRED", "필수 질문에 답변해주세요."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_FORMAT", "이메일 형식이 올바르지 않습니다."),
    INVALID_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_PHONE_FORMAT", "전화번호 형식이 올바르지 않습니다."),
    INVALID_ANSWER_TYPE(HttpStatus.BAD_REQUEST, "INVALID_ANSWER_TYPE", "답변 형식이 올바르지 않습니다."),
    INVALID_BIRTH_DATE_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_BIRTH_DATE_FORMAT", "생년월일 형식이 올바르지 않습니다. (YYYY-MM-DD)"),
    MISSING_CONTACT(HttpStatus.BAD_REQUEST, "MISSING_CONTACT", "이메일을 입력해주세요."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 구독 신청된 이메일입니다."),

    // Curriculum
    CURRICULUM_NOT_FOUND(HttpStatus.NOT_FOUND, "CURRICULUM_NOT_FOUND", "해당 커리큘럼을 찾을 수 없습니다."),

    // Review
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "해당 후기를 찾을 수 없습니다."),

    // FAQ
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND, "FAQ_NOT_FOUND", "해당 FAQ를 찾을 수 없습니다."), 

    // Archive 
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "페이지 값이 올바르지 않습니다."); 

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
