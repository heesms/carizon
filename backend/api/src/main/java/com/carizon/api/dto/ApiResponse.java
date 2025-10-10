package com.carizon.api.dto;

import java.util.Map;

public record ApiResponse<T>(T data, Map<String, Object> meta) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Map.of());
    }

    public static <T> ApiResponse<T> of(T data, Map<String, Object> meta) {
        return new ApiResponse<>(data, meta);
    }
}
