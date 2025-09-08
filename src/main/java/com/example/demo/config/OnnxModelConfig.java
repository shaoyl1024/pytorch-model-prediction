package com.example.demo.config;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.demo.exception.ModelException;
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
 * <p> 2. 创建并管理各版本模型的会话（OrtSession）
 * <p> 3. 提供根据模型版本获取上下文（会话+节点名）的方法
 * @Author charles
 * @Date 2025/9/6 00:28
 * @Version 1.0.0
 */
@Configuration
@Slf4j
public class OnnxModelConfig {

    // -------------------------- 1. 模型配置参数 --------------------------
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

    // ctr_v3 配置
    @Value("${model.ctr_v3.model-path}")
    private Resource onnxModelResourceV3;
    @Value("${model.ctr_v2.input-node}")
    private String inputNodeNameV3;
    @Value("${model.ctr_v2.output-node}")
    private String outputNodeNameV3;

    // -------------------------- 2. 注入ONNX环境（来自OrtEnvironmentConfig） --------------------------
    @Autowired
    private OrtEnvironment ortEnvironment;

    // -------------------------- 3. 注入模型会话Bean（本类中定义） --------------------------
    @Autowired
    @Qualifier("ortSessionV1")
    private OrtSession ortSessionV1;

    @Autowired
    @Qualifier("ortSessionV2")
    private OrtSession ortSessionV2;

    @Autowired
    @Qualifier("ortSessionV3")
    private OrtSession ortSessionV3;

    // -------------------------- 4. 模型会话Bean定义 --------------------------
    @Bean(name = "ortSessionV1", destroyMethod = "close")
    public OrtSession ortSessionV1() throws OrtException, IOException {
        return createOrtSession(onnxModelResourceV1, "ctr_v1");
    }

    @Bean(name = "ortSessionV2", destroyMethod = "close")
    public OrtSession ortSessionV2() throws OrtException, IOException {
        return createOrtSession(onnxModelResourceV2, "ctr_v2");
    }

    @Bean(name = "ortSessionV3", destroyMethod = "close")
    public OrtSession ortSessionV3() throws OrtException, IOException {
        return createOrtSession(onnxModelResourceV3, "ctr_v3");
    }

    // -------------------------- 5. 模型上下文获取方法 --------------------------

    /**
     * 根据模型版本获取对应的模型上下文（会话+输入输出节点名）
     *
     * @param modelVersion 模型版本（如"ctr_v1"、"ctr_v2"）
     * @return 模型上下文
     */
    public ModelContext getModelContext(String modelVersion) {
        ModelContext modelContext;
        switch (modelVersion) {
            case "ctr_v1":
                modelContext = new ModelContext(ortSessionV1, inputNodeNameV1, outputNodeNameV1);
                break;
            case "ctr_v2":
                modelContext = new ModelContext(ortSessionV2, inputNodeNameV2, outputNodeNameV2);
                break;
            case "ctr_v3":
                modelContext = new ModelContext(ortSessionV3, inputNodeNameV3, outputNodeNameV3);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported model version: " + modelVersion + ", supported versions: ctr_v1, ctr_v2"
                );
        }

        log.info("Model context matched successfully - Version: {}, Input Node: {}, Output Node: {}",
                modelVersion, modelContext.getInputNodeName(), modelContext.getOutputNodeName());
        return modelContext;
    }

    // -------------------------- 工具方法：创建ONNX会话 --------------------------

    /**
     * 通用会话创建方法：封装模型文件校验、会话配置逻辑
     * @param modelResource 模型文件资源（从配置文件注入）
     * @param modelName 模型名称（如"ctr_v1"，用于日志区分）
     * @return 配置完成的ONNX会话
     * @throws IOException 模型文件不存在/无法读取时抛出
     * @throws OrtException 会话创建/配置失败时抛出
     */
    private OrtSession createOrtSession(Resource modelResource, String modelName) throws IOException, OrtException {
        // 将Resource转为File，获取模型绝对路径
        File modelFile = modelResource.getFile();
        String modelPath = modelFile.getAbsolutePath();

        // 校验模型文件有效性
        if (!modelFile.exists() || !modelFile.isFile()) {
            String errorMsg = String.format("Model [%s] file invalid: %s", modelName, modelPath);
            log.error(errorMsg);
            throw new ModelException(errorMsg);
        }

        // 配置会话参数（优化推理性能）
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        int cpuCoreNum = Runtime.getRuntime().availableProcessors();
        sessionOptions.setInterOpNumThreads(cpuCoreNum);  // 跨算子线程数
        sessionOptions.setIntraOpNumThreads(cpuCoreNum);  // 算子内线程数
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);  // 全量优化

        // 创建会话
        OrtSession session = ortEnvironment.createSession(modelPath, sessionOptions);
        log.info("Model [{}] loaded successfully - Path: {}", modelName, modelPath);
        return session;
    }
}