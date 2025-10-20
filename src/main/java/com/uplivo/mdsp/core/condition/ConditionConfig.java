package com.uplivo.mdsp.core.condition;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Description 条件配置类，用于定义模型路由规则
 *
 * @Author charles
 * @Date 2025/10/20 17:19
 * @Version 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "condition")
public class ConditionConfig {
    private boolean enabled = true;
    private String defaultModel = "UNK";
    private List<ConditionRule> rules;

    @Data
    public static class ConditionRule {
        private String name;
        private String targetModel;
        private List<Condition> conditions;
        private boolean enabled = true;
    }

    @Data
    public static class Condition {
        private String field;
        private String value;
    }
}