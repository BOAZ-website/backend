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
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "접근 권한이 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "해당 리소스에 접근할 권한이 없습니다."),

    // Auth / Token
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "TOKEN_NOT_FOUND", "토큰이 존재하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_TOKEN", "만료된 토큰입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "ALREADY_MEMBER", "이미 동아리원으로 등록된 유저입니다."),

    // Admin
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "해당 계정을 찾을 수 없습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "DUPLICATE_USERNAME", "이미 존재하는 아이디입니다."),
    CANNOT_MODIFY_OWN_ROLE(HttpStatus.FORBIDDEN, "CANNOT_MODIFY_OWN_ROLE", "본인 계정의 역할은 변경할 수 없습니다."),
    LAST_SUPER_ACCOUNT(HttpStatus.BAD_REQUEST, "LAST_SUPER_ACCOUNT", "마지막 SUPER 계정은 삭제할 수 없습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.UNAUTHORIZED, "INVALID_CURRENT_PASSWORD", "현재 비밀번호가 올바르지 않습니다."),

    // Recruitment
    DUPLICATE_TERM(HttpStatus.CONFLICT, "DUPLICATE_TERM", "이미 존재하는 기수입니다."),
    RECRUITMENT_HAS_REFERENCES(HttpStatus.BAD_REQUEST, "RECRUITMENT_HAS_REFERENCES", "연관된 데이터가 존재하여 삭제할 수 없습니다."),
    DUPLICATE_QUESTION_LABEL(HttpStatus.CONFLICT, "DUPLICATE_QUESTION_LABEL", "이미 존재하는 질문 label입니다."),
    DUPLICATE_QUESTION_ORDER(HttpStatus.CONFLICT, "DUPLICATE_QUESTION_ORDER", "이미 사용 중인 순서 번호입니다."),
    QUESTION_HAS_ANSWERS(HttpStatus.BAD_REQUEST, "QUESTION_HAS_ANSWERS", "연관된 답변 데이터가 존재하여 삭제할 수 없습니다."),
    RECRUITMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECRUITMENT_NOT_FOUND", "해당 모집 공고를 찾을 수 없습니다."),
    RECRUITMENT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "RECRUITMENT_NOT_AVAILABLE", "현재 지원 가능한 기간이 아닙니다."),
    QUESTIONS_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTIONS_NOT_FOUND", "등록된 질문이 없습니다."),
    RECRUITMENT_CLOSED(HttpStatus.CONFLICT, "RECRUITMENT_CLOSED", "모집이 마감되었습니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "ALREADY_SUBMITTED", "이미 제출된 지원서가 있습니다."),
    APPLICATION_ALREADY_SUBMITTED(HttpStatus.FORBIDDEN, "APPLICATION_ALREADY_SUBMITTED", "제출된 지원서에 접근할 수 없습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "지원서를 찾을 수 없습니다."),
    RECRUITMENT_NOT_CLOSED(HttpStatus.BAD_REQUEST, "RECRUITMENT_NOT_CLOSED", "모집이 진행 중인 공고의 지원서는 삭제할 수 없습니다."),
    ANSWER_REQUIRED(HttpStatus.BAD_REQUEST, "ANSWER_REQUIRED", "필수 질문에 답변해주세요."),
    INVALID_TRACK_SELECTION(HttpStatus.BAD_REQUEST, "INVALID_TRACK_SELECTION", "지원 부문을 올바르게 선택해주세요."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_FORMAT", "이메일 형식이 올바르지 않습니다."),
    INVALID_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_PHONE_FORMAT", "전화번호 형식이 올바르지 않습니다."),
    INVALID_ANSWER_TYPE(HttpStatus.BAD_REQUEST, "INVALID_ANSWER_TYPE", "답변 형식이 올바르지 않습니다."),
    INVALID_BIRTH_DATE_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_BIRTH_DATE_FORMAT", "생년월일 형식이 올바르지 않습니다. (YYYY-MM-DD)"),
    MISSING_CONTACT(HttpStatus.BAD_REQUEST, "MISSING_CONTACT", "이메일을 입력해주세요."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 구독 신청된 이메일입니다."),
    S3_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S3_UPLOAD_FAILED", "파일 업로드 중 오류가 발생했습니다."),
    
    // Curriculum
    CURRICULUM_NOT_FOUND(HttpStatus.NOT_FOUND, "CURRICULUM_NOT_FOUND", "해당 커리큘럼을 찾을 수 없습니다."),
    DUPLICATE_TRACK(HttpStatus.CONFLICT, "DUPLICATE_TRACK", "이미 존재하는 트랙입니다."),

    // Review
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "해당 후기를 찾을 수 없습니다."),

    // FAQ
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND, "FAQ_NOT_FOUND", "해당 FAQ를 찾을 수 없습니다."),
    DUPLICATE_ORDER_NUM(HttpStatus.CONFLICT, "DUPLICATE_ORDER_NUM", "동일 카테고리에 같은 순서 번호가 이미 존재합니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "잘못된 요청 값 입니다."),

    // Archive
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "페이지 값이 올바르지 않습니다."),

    // Archive Admin
    ARCHIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARCHIVE_NOT_FOUND", "해당 아카이브를 찾을 수 없습니다."),
    UNSUPPORTED_ARCHIVE_CATEGORY(HttpStatus.BAD_REQUEST, "UNSUPPORTED_ARCHIVE_CATEGORY", "해당 카테고리는 지원하지 않는 작업입니다."),
    MISSING_TERM(HttpStatus.BAD_REQUEST, "MISSING_TERM", "term이 누락되었습니다."),
    MISSING_TITLE(HttpStatus.BAD_REQUEST, "MISSING_TITLE", "title이 누락되었습니다."),
    MISSING_TRACK(HttpStatus.BAD_REQUEST, "MISSING_TRACK", "track이 누락되었습니다."),
    MISSING_LINKS(HttpStatus.BAD_REQUEST, "MISSING_LINKS", "links가 누락되었습니다."),
    MISSING_HALF(HttpStatus.BAD_REQUEST, "MISSING_HALF", "half가 누락되었습니다."),
    INVALID_URL_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_URL_FORMAT", "링크 URL 형식이 올바르지 않습니다."),
    FUTURE_DATE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "FUTURE_DATE_NOT_ALLOWED", "미래 날짜는 입력할 수 없습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "허용되지 않는 이미지 파일 형식입니다. (jpg, jpeg, png, webp)"),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE_SIZE_EXCEEDED", "이미지 파일 크기는 5MB를 초과할 수 없습니다."),
    S3_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S3_DELETE_FAILED", "파일 삭제 중 오류가 발생했습니다.");


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
