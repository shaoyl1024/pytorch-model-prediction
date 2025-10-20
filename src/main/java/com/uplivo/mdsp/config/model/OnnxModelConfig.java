package com.uplivo.mdsp.config.model;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.uplivo.mdsp.common.exception.ModelException;
import com.uplivo.mdsp.config.properties.ModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Slf4j
public class OnnxModelConfig {

    private final OrtEnvironment ortEnvironment;
    private final ResourceLoader resourceLoader;
    private final ModelProperties modelProperties;

    private final Map<String, ModelContext> modelContexts = new ConcurrentHashMap<>();

    public OnnxModelConfig(OrtEnvironment ortEnvironment,
                           ResourceLoader resourceLoader,
                           ModelProperties modelProperties) {
        this.ortEnvironment = ortEnvironment;
        this.resourceLoader = resourceLoader;
        this.modelProperties = modelProperties;
    }

    @PostConstruct
    public void initModels() {
        log.info("Starting dynamic model loading...");
        if (modelProperties.getConfigs() == null || modelProperties.getConfigs().isEmpty()) {
            log.warn("No model configurations found in properties");
            return;
        }

        modelProperties.getConfigs().forEach((modelId, config) -> {
            if (!config.isEnabled()) {
                log.info("Model {} is disabled, skipping", modelId);
                return;
            }
            try {
                loadModel(modelId, config);
                log.info("Successfully loaded model: {}", modelId);
            } catch (Exception e) {
                log.error("Failed to load model: {}", modelId, e);
            }
        });

        log.info("Model loading completed. Loaded: {}", modelContexts.size());
    }

    @Bean
    public Map<String, ModelContext> modelContextMap() {
        return new HashMap<>(modelContexts);
    }

    private void loadModel(String modelId, ModelProperties.ModelConfig config)
            throws OrtException, IOException {

        // 加载模型文件
        Resource modelResource = resourceLoader.getResource(config.getPath());
        if (!modelResource.exists()) {
            throw new ModelException("Model file not found: " + config.getPath());
        }
        File modelFile = modelResource.getFile();
        String modelPath = modelFile.getAbsolutePath();

        // 创建会话配置
        OrtSession.SessionOptions sessionOptions = createSessionOptions();
        OrtSession session = ortEnvironment.createSession(modelPath, sessionOptions);

        // 构建模型上下文
        ModelContext context = ModelContext.builder()
                .modelId(modelId)
                .modelName(config.getName() != null ? config.getName() : modelId)
                .modelVersion(config.getVersion() != null ? config.getVersion() : "1.0")
                .modelType(config.getType() != null ? config.getType() : "UNKNOWN")
                .session(session)
                .inputNodeName(config.getInputNode())
                .outputNodeName(config.getOutputNode())
                .modelPath(config.getPath())
                .enabled(config.isEnabled())
                .description(config.getDescription() != null ? config.getDescription() : "Dynamically loaded model")
                .loadTimestamp(System.currentTimeMillis())
                .build();

        modelContexts.put(modelId, context);

        log.info("Model loaded successfully - ID: {}, Inputs: {}, Outputs: {}",
                modelId, session.getInputInfo().keySet(), session.getOutputInfo().keySet());
    }

    /**
     * 配置会话参数（优化推理性能）
     *
     * @return
     * @throws OrtException
     */
    private OrtSession.SessionOptions createSessionOptions() throws OrtException {
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        int cpuCoreNum = Runtime.getRuntime().availableProcessors();
        sessionOptions.setInterOpNumThreads(Math.max(1, cpuCoreNum / 2));                   // 跨算子线程数
        sessionOptions.setIntraOpNumThreads(cpuCoreNum);                                    // 算子内线程数
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);    // 全量优化
        return sessionOptions;
    }

}
