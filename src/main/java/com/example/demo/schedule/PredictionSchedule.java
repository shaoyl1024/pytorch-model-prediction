package com.example.demo.schedule;

import com.example.demo.data.TestDataLoaderService;
import com.example.demo.domain.PredictionResult;
import com.example.demo.service.impl.CtrV1Service;
import com.example.demo.service.impl.CtrV2Service;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @Description 定时预测任务服务类
 * 核心职责：通过定时任务（基于Cron表达式配置）加载测试数据，模拟线上请求流量，
 * 依次执行“数据加载→特征预处理→模型推理→结果封装与日志输出”全流程，
 * 用于验证模型在类生产环境下的预测稳定性与结果正确性。
 *
 * @Author charles
 * @Date 2025/9/1 21:50
 * @Version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PredictionSchedule {

    private final TestDataLoaderService dataLoaderService;

    private final CtrV1Service ctrV1Service;

    private final CtrV2Service ctrV2Service;

    /**
     * 定时执行预测任务
     * <p>
     * 任务触发时机由配置文件中“dsp.schedule.cron”的Cron表达式控制，
     * 异常场景按类型捕获并输出日志，避免任务中断影响后续调度。
     */
    @Scheduled(cron = "${model.test.cron}")
    public void scheduledPrediction() {
        log.info("=== Scheduled prediction task started ===");

        try {
            // 步骤1：加载测试数据（无数据时返回空列表）
            List<Map<String, String>> rawSamples = dataLoaderService.loadTestData();
            if (rawSamples.isEmpty()) {
                log.warn("No test data available, aborting current prediction task");
                return;
            }
            log.info("Successfully loaded {} test samples from data source", rawSamples.size());

            // 步骤2：使用CTR v2模型执行预测（定时任务默认模型）
            float[] ctrProbabilities = ctrV2Service.predict(rawSamples);
            if (ctrProbabilities.length != rawSamples.size()) {
                log.warn("Prediction result count mismatch: {} probabilities for {} samples",
                        ctrProbabilities.length, rawSamples.size());
            }
            log.info("Model prediction completed successfully");

            // 步骤3：封装结果并打印格式化输出
            List<PredictionResult> predictionResults = dataLoaderService.wrapPredictionResult(rawSamples, ctrProbabilities);
            logPredictionResults(predictionResults);

        } catch (IOException | CsvValidationException e) {
            log.error("Data loading failed during scheduled prediction", e);
        } catch (Exception e) {
            log.error("Unexpected error occurred in scheduled prediction task", e);
        } finally {
            log.info("=== Scheduled prediction task finished ===\n");
        }
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
                    result.getSampleId(),result.getCtrProbabilityFormatted());
        }
    }
}