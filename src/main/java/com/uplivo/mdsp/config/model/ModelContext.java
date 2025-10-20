package com.uplivo.mdsp.config.model;

import ai.onnxruntime.OrtSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * @Description 模型上下文封装类：关联模型的会话、输入节点名、输出节点名、元数据
 * @Author charles
 * @Date 2025/9/6 00:28
 * @Version 1.0.0
 */
@Slf4j
@Getter
@Builder
@AllArgsConstructor
@ToString(exclude = "session")
public class ModelContext {

    /**
     * 模型唯一标识
     */
    private final String modelId;

    /**
     * 模型名称
     */
    private final String modelName;

    /**
     * 模型版本
     */
    private final String modelVersion;

    /**
     * 模型类型 (CTR, DeepFM, etc.)
     */
    private final String modelType;

    /**
     * 模型的ONNX会话
     */
    private final OrtSession session;

    /**
     * 模型的输入节点名
     */
    private final String inputNodeName;

    /**
     * 模型的输出节点名
     */
    private final String outputNodeName;

    /**
     * 模型输入形状信息
     */
    private final Map<String, long[]> inputShapes;

    /**
     * 模型输出形状信息
     */
    private final Map<String, long[]> outputShapes;

    /**
     * 模型文件路径
     */
    private final String modelPath;

    /**
     * 模型是否启用
     */
    @Builder.Default
    private final boolean enabled = true;

    /**
     * 模型描述
     */
    private final String description;

    /**
     * 模型加载时间
     */
    private final long loadTimestamp;

    // 简化构造函数，保持向后兼容
    public ModelContext(OrtSession session, String inputNodeName, String outputNodeName) {
        this("unknown", "unknown", "unknown", "unknown", session, inputNodeName, outputNodeName,
                null, null, "unknown", true, "Legacy model context", System.currentTimeMillis());
    }

    /**
     * 验证模型上下文是否有效
     */
    public boolean isValid() {
        return session != null &&
                inputNodeName != null && !inputNodeName.trim().isEmpty() &&
                outputNodeName != null && !outputNodeName.trim().isEmpty() &&
                enabled;
    }

    /**
     * 获取简化的字符串表示（用于日志）
     */
    public String toSimpleString() {
        return String.format("ModelContext{id='%s', name='%s', version='%s', type='%s', input='%s', output='%s'}",
                modelId, modelName, modelVersion, modelType, inputNodeName, outputNodeName);
    }

}