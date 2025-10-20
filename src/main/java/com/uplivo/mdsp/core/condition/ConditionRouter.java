package com.uplivo.mdsp.core.condition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * @Description 条件路由类，根据特征条件选择模型
 *
 * @Author charles
 * @Date 2025/10/20 17:05
 * @Version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionRouter {

    private final ConditionConfig conditionConfig;

    public Map<String, List<Map<String, String>>> groupFeaturesByCondition(List<Map<String, String>> features) {
        Map<String, List<Map<String, String>>> groupedFeatures = new HashMap<>();

        if (features == null || features.isEmpty()) {
            return groupedFeatures;
        }

        // 为每个样本选择模型
        List<String> modelList = batchRouteByCondition(features);

        // 按模型分组特征
        for (int i = 0; i < features.size(); i++) {
            String model = modelList.get(i);
            Map<String, String> feature = features.get(i);

            groupedFeatures.computeIfAbsent(model, k -> new ArrayList<>()).add(feature);
        }

        return groupedFeatures;
    }

    public List<String> batchRouteByCondition(List<Map<String, String>> features) {
        if (!conditionConfig.isEnabled() || features == null) {
            return createDefaultList(features);
        }

        List<String> modelList = new ArrayList<>();

        for (Map<String, String> sample : features) {
            String selectedModel = routeSingleSample(sample);
            modelList.add(selectedModel);
        }

        return modelList;
    }

    private String routeSingleSample(Map<String, String> sample) {
        if (conditionConfig.getRules() == null) {
            return conditionConfig.getDefaultModel();
        }

        for (ConditionConfig.ConditionRule rule : conditionConfig.getRules()) {
            if (rule.isEnabled() && matchesConditions(rule.getConditions(), sample)) {
                return rule.getTargetModel();
            }
        }

        return conditionConfig.getDefaultModel();
    }

    private boolean matchesConditions(List<ConditionConfig.Condition> conditions, Map<String, String> features) {
        if (conditions == null) return true;

        for (ConditionConfig.Condition condition : conditions) {
            String fieldValue = features.get(condition.getField());
            if (fieldValue == null || !fieldValue.equals(condition.getValue())) {
                return false;
            }
        }
        return true;
    }

    private List<String> createDefaultList(List<Map<String, String>> features) {
        List<String> defaultList = new ArrayList<>();
        if (features != null) {
            for (int i = 0; i < features.size(); i++) {
                defaultList.add(conditionConfig.getDefaultModel());
            }
        }
        return defaultList;
    }

}
