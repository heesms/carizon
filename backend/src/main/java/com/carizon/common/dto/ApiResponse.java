package com.carizon.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공통 API 응답 형식
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private String errorCode; // 선택적
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                null,
                data,
                LocalDateTime.now(),
                null
        );
    }
    
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                true,
                message,
                data,
                LocalDateTime.now(),
                null
        );
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(
                false,
                message,
                null,
                LocalDateTime.now(),
                null
        );
    }
    
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(
                false,
                message,
                null,
                LocalDateTime.now(),
                errorCode
        );
    }
}
