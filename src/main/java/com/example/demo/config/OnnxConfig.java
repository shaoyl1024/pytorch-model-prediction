package com.example.demo.config;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.demo.exception.ModelException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;

/**
 * ONNX 模型会话配置类
 * <p>
 * 核心职责：创建并配置 ONNX Runtime 的环境（OrtEnvironment）和会话（OrtSession），
 * 负责模型加载、会话参数设置（如优化级别、内存限制），并确保资源在应用生命周期内
 * 正确初始化与释放，为模型预测提供高性能的推理环境。
 *
 * @author charles
 * @version 2.0.0
 * @date 2025/9/4 14:30
 */
@Configuration
@Slf4j
@Data
public class OnnxConfig {

    /**
     * ONNX 模型文件路径（从配置文件读取）
     */
    @Value("${model.ctr_v2.model-path}")
    private Resource onnxModelResource;

    @Value("${model.ctr_v2.input-node}")
    private String inputNodeName; // 输入节点名（如"criteo_features"）

    @Value("${model.ctr_v2.output-node}")
    private String outputNodeName; // 输出节点名（如"ctr_prob"）

    /**
     * 初始化 ONNX 环境（全局单例）
     */
    @Bean(destroyMethod = "close")
    public OrtEnvironment ortEnvironment() {
        try {
            return OrtEnvironment.getEnvironment();
        } catch (Exception e) {
            log.error("创建ONNX环境失败", e);
            throw new ModelException("创建ONNX环境失败", e);
        }
    }

    /**
     * 初始化 ONNX 会话（通过文件路径加载，兼容所有版本）
     */
    @Bean(destroyMethod = "close")
    public OrtSession ortSession(OrtEnvironment ortEnvironment) throws OrtException, IOException {
        // 1. 将 Resource 转为 File（获取模型绝对路径）
        File modelFile = onnxModelResource.getFile();
        // 2. 验证模型文件有效性
        if (!modelFile.exists() || !modelFile.isFile()) {
            log.error("Failed to create ONNX session from path: {}", modelFile.getAbsolutePath());
        }

        // 3. 配置会话参数（CPU 多线程加速）
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        sessionOptions.setInterOpNumThreads(Runtime.getRuntime().availableProcessors());
        sessionOptions.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());

        // 4. 关键修复：通过文件路径创建会话（兼容低版本 ONNX Runtime）
        OrtSession session = ortEnvironment.createSession(
                modelFile.getAbsolutePath(),  // 传入模型绝对路径（String 类型）
                sessionOptions
        );

        // 5. 验证加载结果
        log.info("ONNX model loaded successfully from path: {}", modelFile.getAbsolutePath());
        log.info("ONNX session created with input nodes: {}", session.getInputNames());
        log.info("ONNX session created with output nodes: {}", session.getOutputNames());

        return session;
    }
}