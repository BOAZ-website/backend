package com.boaz.backend.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int status;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private final T data;
    private final String errorCode;
    private final String message;

    // 성공 응답
    private ApiResponse(int status, T data) {
        this.status = status;
        this.data = data;
        this.errorCode = null;
        this.message = null;
    }

    // 실패 응답
    private ApiResponse(int status, String errorCode, String message) {
        this.status = status;
        this.data = null;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), data);
    }

    public static ApiResponse<Void> error(int status, String errorCode, String message) {
        return new ApiResponse<>(status, errorCode, message);
    }
}