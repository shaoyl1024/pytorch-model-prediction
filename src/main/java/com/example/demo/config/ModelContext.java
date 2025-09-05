package com.example.demo.config;

import ai.onnxruntime.OrtSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Description 模型上下文封装类：关联模型的会话、输入节点名、输出节点名
 * @Author charles
 * @Date 2025/9/6 00:28
 * @Version 1.0.0
 */
@Getter
@AllArgsConstructor
public class ModelContext {
    private final OrtSession session;       // 模型的ONNX会话
    private final String inputNodeName;     // 模型的输入节点名
    private final String outputNodeName;    // 模型的输出节点名
}