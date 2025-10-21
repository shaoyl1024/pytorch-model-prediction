package com.uplivo.mdsp.common.response;

import com.uplivo.mdsp.common.enums.ErrorCode;
import lombok.Data;

/**
 * @Description API响应类：统一响应格式
 * @Author charles
 * @Date 2025/10/20 15:57
 * @Version 1.0.0
 */
@Data
public class ApiResponse<T> {
    private boolean success;
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public ApiResponse(boolean success, int code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // 成功响应方法
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, 0, "Success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, 0, message, data);
    }

    // 错误响应方法
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, ErrorCode.SYSTEM_ERROR.getCode(), message, null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String customMessage) {
        return new ApiResponse<>(false, errorCode.getCode(), customMessage, null);
    }
}