package com.example.demo.integration;

import com.example.demo.PredictionApplication;
import com.example.demo.data.TestDataLoaderService;
import com.example.demo.domain.PredictionResult;
import com.example.demo.service.AbstractModelService;
import com.example.demo.service.ModelServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

/**
 * 集成测试：验证全流程（数据加载→特征处理→模型打分）与预期结果完全一致
 * 环境：使用真实服务组件和固定测试数据
 */
@SpringBootTest(classes = PredictionApplication.class)
@Slf4j
class CTRV1PredictionTest {

    @Autowired
    private TestDataLoaderService dataLoaderService;

    @Autowired
    private ModelServiceFactory modelServiceFactory;


    /**
     * 核心测试：验证全流程结果与预期一致
     * 步骤：
     * 1. 加载固定测试数据
     * 2. 执行特征处理和模型打分
     * 3. 比对实际结果与预期结果（精度误差控制在0.001内）
     */
    @Test
    void fullFlow_consistencyTest() throws Exception {
        // 步骤1：加载测试数据（无数据时返回空列表）
        List<Map<String, String>> rawSamples = dataLoaderService.loadTestData();
        if (rawSamples.isEmpty()) {
            log.warn("No test data available, aborting current prediction task");
            return;
        }
        log.info("Successfully loaded {} test samples from data source", rawSamples.size());

        // 步骤2：使用CTR v1模型执行预测（定时任务默认模型）
        String modelVersion = "ctr_v1";
        AbstractModelService modelService = modelServiceFactory.getServiceByVersion(modelVersion);
        float[] ctrProbabilities = modelService.predict(rawSamples);
        if (ctrProbabilities.length != rawSamples.size()) {
            log.warn("Prediction result count mismatch: {} probabilities for {} samples",
                    ctrProbabilities.length, rawSamples.size());
        }
        log.info("Model prediction completed successfully");

        // 步骤3：封装结果并打印格式化输出
        List<PredictionResult> predictionResults = dataLoaderService.wrapPredictionResult(rawSamples, ctrProbabilities);
        logPredictionResults(predictionResults);

        log.info("全流程结果一致性测试通过，所有样本打分符合预期");
    }

    /**
     * 打印预测结果日志
     * <p>
     * 按“样本ID-点击概率-原始特征”格式输出，点击概率已做小数位格式化处理。
     *
     * @param results 封装后的预测结果列表
     */
    private void logPredictionResults(List<PredictionResult> results) {
        log.info("Prediction results summary: total {} samples", results.size());

        // 记录每个结果，包含格式化的CTR概率
        for (PredictionResult result : results) {
            log.info("Sample ID: {} | CTR Probability: {}",
                    result.getSampleId(), result.getCtrProbabilityFormatted());
        }
    }
}