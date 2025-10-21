package com.uplivo.mdsp.common.exception;

import com.uplivo.mdsp.common.enums.ErrorCode;
import com.uplivo.mdsp.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @Description 全局异常处理器：统一响应格式
 * @Author charles
 * @Date 2025/10/20 15:56
 * @Version 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ModelException.class)
    public ApiResponse<?> handleModelException(ModelException e) {
        log.error("Model exception occurred: code={}, message={}", e.getCode(), e.getMessage(), e);
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Invalid request parameter: {}", e.getMessage(), e);
        return ApiResponse.error(ErrorCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleGeneralException(Exception e) {
        log.error("Unexpected error occurred", e);
        return ApiResponse.error(ErrorCode.SYSTEM_ERROR);
    }
}