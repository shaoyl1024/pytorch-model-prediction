package com.example.demo.config;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.demo.exception.ModelException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;

/**
 * @Description ONNX模型配置类
 * 核心职责：
 * <p> 1. 加载模型的配置参数（路径、输入输出节点名）
 * <p> 2. 创建并管理ONNX全局环境（OrtEnvironment）和模型会话（OrtSession）
 * <p> 3. 提供根据模型版本获取上下文（会话+节点名）的方法，为预测服务提供支持
 * @Author charles
 * @Date 2025/9/6 00:28
 * @Version 1.0.0
 */
@Configuration
@Slf4j
@Data
public class OnnxConfig {

    // -------------------------- 1. 基础配置参数 --------------------------
    // ctr_v1 配置
    @Value("${model.ctr_v1.model-path}")
    private Resource onnxModelResourceV1;
    @Value("${model.ctr_v1.input-node}")
    private String inputNodeNameV1;
    @Value("${model.ctr_v1.output-node}")
    private String outputNodeNameV1;

    // ctr_v2 配置
    @Value("${model.ctr_v2.model-path}")
    private Resource onnxModelResourceV2;
    @Value("${model.ctr_v2.input-node}")
    private String inputNodeNameV2;
    @Value("${model.ctr_v2.output-node}")
    private String outputNodeNameV2;

    // -------------------------- 2. 注入模型会话Bean --------------------------
    // 注入 ctr_v1 的会话（对应下方 ortSessionV1() 方法的Bean）
    @Autowired
    @Qualifier("ortSessionV1")
    private OrtSession ortSessionV1;

    // 注入 ctr_v2 的会话（对应下方 ortSessionV2() 方法的Bean）
    @Autowired
    @Qualifier("ortSessionV2")
    private OrtSession ortSessionV2;

    // -------------------------- 3. 核心Bean定义 --------------------------
    @Bean(name = "ortSessionV1", destroyMethod = "close")
    public OrtSession ortSessionV1(OrtEnvironment ortEnvironment) throws OrtException, IOException {
        return createOrtSession(ortEnvironment, onnxModelResourceV1, "ctr_v1");
    }

    @Bean(name = "ortSessionV2", destroyMethod = "close")
    public OrtSession ortSessionV2(OrtEnvironment ortEnvironment) throws OrtException, IOException {
        return createOrtSession(ortEnvironment, onnxModelResourceV2, "ctr_v2");
    }

    /**
     * 创建ONNX全局环境（单例）
     * <p>环境是ONNX Runtime的核心，管理线程池、内存分配等全局资源，所有模型共享一个环境
     *
     * @return 全局ONNX环境实例
     * @throws ModelException 环境创建失败时抛出业务异常
     */
    @Bean(destroyMethod = "close")
    public OrtEnvironment ortEnvironment() {
        try {
            return OrtEnvironment.getEnvironment();
        } catch (Exception e) {
            log.error("Failed to create ONNX environment", e);
            throw new ModelException("Failed to create ONNX environment", e);
        }
    }

    // -------------------------- 4. 模型上下文获取方法 --------------------------

    /**
     * 根据模型版本获取对应的模型上下文（会话+输入输出节点名）
     *
     * @param modelVersion 模型版本（如"v1"、"v2"，null/空串默认"v1"）
     * @return 模型上下文
     */
    public ModelContext getModelContext(String modelVersion) {
        String actualVersion = (modelVersion == null || modelVersion.trim().isEmpty())
                ? "v1"
                : modelVersion.trim().toLowerCase();

        ModelContext modelContext;
        switch (actualVersion) {
            case "v1":
                modelContext = new ModelContext(ortSessionV1,
                        inputNodeNameV1, outputNodeNameV1
                );
                break;
            case "v2":
                modelContext = new ModelContext(ortSessionV2,
                        inputNodeNameV2, outputNodeNameV2
                );
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported model version: " + modelVersion + ", only setting version allowed (case-insensitive)"
                );
        }

        log.info("Model context matched successfully - Version: {}, Input Node: {}, Output Node: {}",
                actualVersion, modelContext.getInputNodeName(), modelContext.getOutputNodeName());
        return modelContext;
    }

    // -------------------------- 工具方法：创建ONNX会话 --------------------------

    /**
     * 通用会话创建方法：封装模型文件校验、会话配置逻辑，避免重复代码
     * @param env ONNX全局环境
     * @param modelResource 模型文件资源（从配置文件注入）
     * @param modelName 模型名称（如"ctr_v1"，用于日志区分）
     * @return 配置完成的ONNX会话
     * @throws IOException 模型文件不存在/无法读取时抛出
     * @throws OrtException 会话创建/配置失败时抛出
     */
    private OrtSession createOrtSession(OrtEnvironment env, Resource modelResource, String modelName) throws IOException, OrtException {
        // 将Resource转为File，获取模型绝对路径
        File modelFile = modelResource.getFile();
        String modelPath = modelFile.getAbsolutePath();

        // 校验模型文件有效性：存在且是文件（非目录）
        if (!modelFile.exists() || !modelFile.isFile()) {
            String errorMsg = String.format("Model [%s] file does not exist or is not a valid file: %s", modelName, modelPath);
            log.error(errorMsg);
            throw new ModelException(errorMsg);
        }

        // 配置会话参数：优化推理性能
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        int cpuCoreNum = Runtime.getRuntime().availableProcessors(); // 获取CPU核心数，最大化利用硬件资源
        sessionOptions.setInterOpNumThreads(cpuCoreNum); // 跨算子线程数：控制不同算子间的并行
        sessionOptions.setIntraOpNumThreads(cpuCoreNum); // 算子内线程数：控制单个算子的并行（如矩阵运算）
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT); // 开启全量优化（算子融合、内存优化等）

        // 创建并返回会话
        OrtSession session = env.createSession(modelPath, sessionOptions);
        log.info("Model [{}] loaded successfully - Path: {}", modelName, modelPath);
        return session;
    }
}