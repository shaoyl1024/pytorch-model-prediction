package com.uplivo.mdsp.controller;

import com.uplivo.mdsp.common.response.ApiResponse;
import com.uplivo.mdsp.core.condition.ConditionRouter;
import com.uplivo.mdsp.domain.request.FeatureRequest;
import com.uplivo.mdsp.domain.response.ScoreResponse;
import com.uplivo.mdsp.service.ModelServiceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description 模型服务RPC接口控制器，提供RESTful API作为RPC服务
 * @Author charles
 * @Date 2025/9/8 11:20
 * @Version 1.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model")
public class PredictController {

    private final ModelServiceFactory modelFactory;
    private final ConditionRouter conditionRouter;

    /**
     * 多样本打分接口
     */
    @PostMapping("/predict")
    public ApiResponse<ScoreResponse> predict(@RequestBody FeatureRequest request) {
        log.info("Received prediction request: requestId={}, sampleCount={}",
                request.getRequestId(), request.getFeatures().size());

        // 按条件路由到不同模型
        Map<String, List<Map<String, String>>> featuresByModel =
                conditionRouter.groupFeaturesByCondition(request.getFeatures());

        // 批量预测
        Map<String, float[]> predictions = batchPredict(featuresByModel);

        // 合并预测结果
        float[] scores = mergeResults(request.getFeatures(), featuresByModel, predictions);

        ScoreResponse trafficScoreResponse = ScoreResponse.builder()
                .requestId(request.getRequestId()).scores(scores)
                .build();

        return ApiResponse.success(trafficScoreResponse);
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getModelInfo(@RequestParam String modelVersion) {
        return ApiResponse.success(modelFactory.getServiceByVersion(modelVersion).getModelInfo());
    }


    private Map<String, float[]> batchPredict(Map<String, List<Map<String, String>>> featuresByModel) {
        Map<String, float[]> results = new HashMap<>();
        featuresByModel.forEach((model, features) -> {
            try {
                results.put(model, modelFactory.getServiceByVersion(model).predict(features));
            } catch (Exception e) {
                log.error("Prediction failed for model: {}", model, e);
                results.put(model, new float[features.size()]);
                Arrays.fill(results.get(model), -1f); // 失败填充-1
            }
        });
        return results;
    }
    
    private float[] mergeResults(List<Map<String, String>> original,
                                 Map<String, List<Map<String, String>>> grouped,
                                 Map<String, float[]> predictions) {
        float[] merged = new float[original.size()];
        Map<Map<String, String>, Integer> featureIndexMap = new HashMap<>();
        for (int i = 0; i < original.size(); i++) {
            featureIndexMap.put(original.get(i), i);
        }

        grouped.forEach((model, features) -> {
            float[] preds = predictions.get(model);
            for (int i = 0; i < features.size(); i++) {
                Integer originalIdx = featureIndexMap.get(features.get(i));
                if (originalIdx != null && i < preds.length) {
                    merged[originalIdx] = preds[i];
                }
            }
        });
        return merged;
    }
}
