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

    SYSTEM_ERROR(10000, "系统内部错误"),
    PARAM_ERROR(10001, "参数非法"),

    // 模型相关错误
    MODEL_LOAD_FAILED(20000, "模型加载失败"),
    MODEL_NOT_FOUND(20001, "模型不存在"),
    MODEL_INFERENCE_FAILED(20002, "模型推理失败"),

    // 预处理相关错误
    PREPROCESSOR_INIT_FAILED(30000, "预处理配置初始化失败"),
    FEATURE_INVALID(30001, "特征数据无效");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
