package com.example.demo.schedule;

import com.example.demo.model.PredictionResult;
import ai.onnxruntime.OrtException;
import com.opencsv.exceptions.CsvValidationException;
import com.example.demo.service.ModelPredictionService;
import com.example.demo.service.PreprocessorService;
import com.example.demo.service.TestDataLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 定时预测任务服务类
 * <p>
 * 核心职责：通过定时任务（基于Cron表达式配置）加载测试数据，模拟线上请求流量，
 * 依次执行“数据加载→特征预处理→模型推理→结果封装与日志输出”全流程，
 * 用于验证模型在类生产环境下的预测稳定性与结果正确性。
 *
 * @author charles
 * @date 2025/9/3 22:45
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PredictionSchedule {

    // 数据加载服务：负责读取测试数据文件（如TSV/CSV）
    private final TestDataLoaderService dataLoaderService;
    // 特征预处理服务：将原始数据转换为模型可识别的标准化特征
    private final PreprocessorService preprocessorService;
    // 模型预测服务：加载ONNX模型并执行批量推理
    private final ModelPredictionService predictionService;

    // 从配置文件读取批量预测大小（每次任务处理的样本数量）
    @Value("${model.onnx.batch-size}")
    private int batchSize;

    /**
     * 定时执行预测任务
     * <p>
     * 任务触发时机由配置文件中“dsp.schedule.cron”的Cron表达式控制，
     * 异常场景按类型捕获并输出日志，避免任务中断影响后续调度。
     */
    @Scheduled(cron = "${model.schedule.cron}")
    public void scheduledPrediction() {
        log.info("=== Scheduled prediction task started ===");
        try {
            // 1. 加载测试数据（按配置的批量大小读取）
            List<Map<String, String>> rawSamples = dataLoaderService.loadTestData(batchSize);
            if (rawSamples.isEmpty()) {
                log.warn("No test data loaded, skipping current prediction task");
                return;
            }
            log.info("Successfully loaded {} test samples", rawSamples.size());

            // 2. 批量预处理原始数据（转换为模型输入格式）
            float[][] modelInputs = preprocessorService.preprocessBatch(rawSamples);
            log.info("Data preprocessing completed, input dimension: {} samples × {} features",
                    modelInputs.length, modelInputs[0].length);

            // 3. 模型批量预测（输出每条样本的点击概率）
            double[] ctrProbs = predictionService.predictBatch(modelInputs);
            log.info("Model prediction completed, obtained {} CTR probability results", ctrProbs.length);

            // 4. 封装预测结果（关联原始样本与预测概率）
            List<PredictionResult> results = dataLoaderService.wrapPredictionResult(rawSamples, ctrProbs);
            // 打印结果日志
            printResults(results);

        } catch (IOException | CsvValidationException e) {
            log.error("Test data loading failed", e);
        } catch (OrtException e) {
            log.error("ONNX model inference failed", e);
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
     * @param results 封装后的预测结果列表
     */
    private void printResults(List<PredictionResult> results) {
        log.info("Prediction results (total {} samples):", results.size());
        for (PredictionResult result : results) {
            log.info("Sample ID: {} | CTR Probability: {} | Raw Features: {}",
                    result.getSampleId(),
                    result.getCtrProbabilityFormatted(),
                    result.getRawFeatures());
        }
    }
}