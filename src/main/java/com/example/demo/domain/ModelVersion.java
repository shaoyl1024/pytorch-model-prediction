package com.example.demo.domain;

/**
 * @Description 模型版本枚举，约束支持的模型类型
 * @Author charles
 * @Date 2025/9/5 22:47
 * @Version 1.0.0
 */
public enum ModelVersion {
    V1("v1", "pickleBasedPreprocessor", "ortSessionV1"),  // 模型1：关联pickle预处理器和v1会话
    V2("v2", "configBasedPreprocessor", "ortSessionV2");  // 模型2：关联json预处理器和v2会话

    private final String code;  // 版本标识（如"v1"）
    private final String preprocessorBeanName;  // 对应的预处理器Bean名
    private final String predictionSessionName;  // 对应的预测会话Bean名

    ModelVersion(String code, String preprocessorBeanName, String predictionSessionName) {
        this.code = code;
        this.preprocessorBeanName = preprocessorBeanName;
        this.predictionSessionName = predictionSessionName;
    }

    // 根据版本标识（如"v1"）获取枚举，找不到则抛异常
    public static ModelVersion getByCode(String code) {
        for (ModelVersion version : values()) {
            if (version.code.equalsIgnoreCase(code)) {
                return version;
            }
        }
        throw new IllegalArgumentException("不支持的模型版本：" + code + "，支持版本：v1、v2");
    }

    // getter
    public String getCode() {
        return code;
    }

    public String getPreprocessorBeanName() {
        return preprocessorBeanName;
    }

    public String getPredictionSessionName() {
        return predictionSessionName;
    }
}
