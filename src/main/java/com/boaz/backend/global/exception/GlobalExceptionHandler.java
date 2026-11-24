package com.boaz.backend.global.exception;

import com.boaz.backend.global.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("CustomException: {}", errorCode.getMessage());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(
                        errorCode.getHttpStatus().value(),
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "입력값이 올바르지 않습니다.";
        log.warn("ValidationException: field={}", fieldError != null ? fieldError.getField() : null);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT_VALUE.getCode(), message));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("MissingPartException: {}", e.getRequestPartName());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        400,
                        ErrorCode.MISSING_PARAMETER.getCode(),
                        ErrorCode.MISSING_PARAMETER.getMessage()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("MissingParameterException: {}", e.getParameterName());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        400,
                        ErrorCode.MISSING_PARAMETER.getCode(),
                        ErrorCode.MISSING_PARAMETER.getMessage()
                ));
    }
    
    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingCookie(MissingRequestCookieException e) {
        log.warn("MissingCookieException: {}", e.getCookieName());
        return ResponseEntity
                .status(ErrorCode.TOKEN_NOT_FOUND.getHttpStatus())
                .body(ApiResponse.error(
                        ErrorCode.TOKEN_NOT_FOUND.getHttpStatus().value(), 
                        ErrorCode.TOKEN_NOT_FOUND.getCode(), 
                        ErrorCode.TOKEN_NOT_FOUND.getMessage()
                ));
    }


    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        String message = ErrorCode.INVALID_INPUT_VALUE.getMessage();
        
        if (e.getCause() instanceof InvalidFormatException ife) {
                if (!ife.getPath().isEmpty()) {
                String fieldName = ife.getPath().get(0).getFieldName();
                message = String.format("'%s' 필드의 값이 유효하지 않습니다. (입력값: %s)", 
                                        fieldName, ife.getValue());
                }
        }

        // 예외 메시지에 입력값이 들어 있어 남기지 않는다. 필드 이름과 원인 예외 종류만 남긴다.
        log.warn("HttpMessageNotReadableException: field={}, cause={}",
                resolveFieldName(e), e.getMostSpecificCause().getClass().getSimpleName());
        
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        400, 
                        ErrorCode.INVALID_INPUT_VALUE.getCode(), 
                        message
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        
        String paramName = e.getName();
        String message = String.format("파라미터 '%s'의 값이 올바르지 않습니다.", paramName);
        
        // 예외 메시지에 입력값이 들어 있어 예외 객체는 남기지 않는다.
        log.warn("TypeMismatchException: parameter={}", paramName);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        400,
                        ErrorCode.INVALID_PARAMETER_TYPE.getCode(),
                        message
                ));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            return handleErrorResponse(e, errorResponse);
        }
        log.error("UnhandledException: ", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error(
                        errorCode.getHttpStatus().value(),
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    /**
     * Spring이 상태 코드를 붙여 던지는 예외(404 {@code NoResourceFoundException}, 405, 415 등)는 그 상태 코드로 응답한다.
     * 탐색 봇 요청이 500·ERROR로 집계되지 않게 하려는 것이다. 위에 따로 처리기가 있는 예외(400 계열)는 여기로 오지 않는다.
     * 예외 메시지에는 요청 경로·입력값이 들어갈 수 있어 남기지 않는다.
     */
    private ResponseEntity<ApiResponse<Void>> handleErrorResponse(Exception e, ErrorResponse errorResponse) {
        HttpStatusCode statusCode = errorResponse.getStatusCode();
        if (statusCode.is5xxServerError()) {
            log.error("{}: status={}", e.getClass().getSimpleName(), statusCode.value(), e);
        } else {
            log.warn("{}: status={}", e.getClass().getSimpleName(), statusCode.value());
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = status != null ? status.name() : String.valueOf(statusCode.value());
        String message = status != null ? status.getReasonPhrase() : code;
        return ResponseEntity
                .status(statusCode)
                .headers(errorResponse.getHeaders())
                .body(ApiResponse.error(statusCode.value(), code, message));
    }

    private static String resolveFieldName(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            return jme.getPath().get(0).getFieldName();
        }
        return null;
    }
}
