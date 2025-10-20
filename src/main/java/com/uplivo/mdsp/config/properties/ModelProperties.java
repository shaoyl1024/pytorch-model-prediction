package com.uplivo.mdsp.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @Description 模型配置参数：从配置文件加载
 * @Author charles
 * @Date 2025/10/20 15:46
 * @Version 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "model")
public class ModelProperties {
    private Map<String, ModelConfig> configs;

    @Data
    public static class ModelConfig {
        private String path;
        private String preprocessorPath;
        private String inputNode;
        private String outputNode;
        private boolean enabled = true;
        private String name;
        private String version;
        private String type;
        private String description;
    }
}