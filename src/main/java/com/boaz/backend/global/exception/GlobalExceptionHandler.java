package com.boaz.backend.global.exception;

import com.boaz.backend.global.common.ApiResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import lombok.extern.slf4j.Slf4j;
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
        log.warn("ValidationException: {}", message);
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

        log.warn("Payload Error: {}", e.getMostSpecificCause().getMessage());
        
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
        
        log.warn("TypeMismatchException: {}", message, e);
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
}
