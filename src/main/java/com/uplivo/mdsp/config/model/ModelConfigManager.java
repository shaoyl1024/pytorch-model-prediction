package com.uplivo.mdsp.config.model;

import com.uplivo.mdsp.common.exception.ModelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description 模型配置管理器：统一管理所有模型上下文
 * @Author charles
 * @Date 2025/10/20 19:57
 * @Version 1.0.0
 */
@Component
@Slf4j
public class ModelConfigManager {

    private final Map<String, ModelContext> modelContextMap;

    @Autowired
    public ModelConfigManager(Map<String, ModelContext> modelContextMap) {
        this.modelContextMap = new ConcurrentHashMap<>();

        log.info("Initializing ModelConfigManager with {} model contexts",
                modelContextMap != null ? modelContextMap.size() : 0);

        if (modelContextMap != null && !modelContextMap.isEmpty()) {
            modelContextMap.forEach((modelId, context) -> {
                if (context != null && context.isValid()) {
                    this.modelContextMap.put(modelId, context);
                    log.info("Registered model: {} - {}", modelId, context.toSimpleString());
                } else {
                    log.warn("Skipping invalid model context: {}", modelId);
                }
            });
        } else {
            log.warn("No model contexts provided to ModelConfigManager");
        }

        log.info("ModelConfigManager initialized successfully with {} models", this.modelContextMap.size());
    }

    public ModelContext getModelContext(String modelId) {
        ModelContext context = modelContextMap.get(modelId);
        if (context == null) {
            throw new ModelException(
                    "Model not found: " + modelId + ". Available models: " + modelContextMap.keySet()
            );
        }

        if (!context.isValid()) {
            throw new ModelException("Model context is invalid: " + modelId);
        }

        return context;
    }

}