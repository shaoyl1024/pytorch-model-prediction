package com.uplivo.mdsp.common.enums;

import lombok.Getter;

/**
 * @Description 错误码枚举定义
 * @Author charles
 * @Date 2025/10/17 16:47
 * @Version 1.0.0
 */
@Getter
public enum ErrorCode {

    SYSTEM_ERROR(10000, "System internal error"),
    PARAM_ERROR(10001, "Invalid parameters"),

    // 模型相关错误
    MODEL_LOAD_FAILED(20000, "Model loading failed"),
    MODEL_NOT_FOUND(20001, "Model not found"),
    MODEL_INFERENCE_FAILED(20002, "Model inference failed"),

    // 预处理相关错误
    PREPROCESSOR_INIT_FAILED(30000, "Preprocessing configuration initialization failed"),
    FEATURE_INVALID(30001, "Invalid feature data");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
