package com.roti5dao.common.web;

import com.roti5dao.common.exception.ErrorCode;
import java.time.Instant;
import java.util.List;

public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

    public record ApiError(String code, String message, List<FieldErrorItem> fields) {
    }

    public record FieldErrorItem(String field, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, Instant.now());
    }

    public static ApiResponse<Void> fail(ErrorCode code, String message, List<FieldErrorItem> fields) {
        return new ApiResponse<>(false, null, new ApiError(code.name(), message, fields == null ? List.of() : fields), Instant.now());
    }

    public static ApiResponse<Void> fail(ErrorCode code) {
        return fail(code, code.defaultMessage(), List.of());
    }
}
