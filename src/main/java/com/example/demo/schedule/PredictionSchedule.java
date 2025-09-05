package com.example.demo.schedule;

import com.alibaba.fastjson.JSONObject;
import com.example.demo.model.PredictionResult;
import com.example.demo.service.DataLoaderService;
import com.example.demo.service.PreprocessorService;
import com.example.demo.service.PredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
@RequiredArgsConstructor
public class PredictionSchedule {

    private final DataLoaderService dataLoaderService;
    private final PreprocessorService preprocessorService;
    private final PredictionService predictionService;

    // 防止任务并发执行的锁
    private final ReentrantLock taskLock = new ReentrantLock();

    @Scheduled(cron = "${schedule.cron:0 */5 * * * ?}")
    public void executePrediction() {
        log.info("===== 开始执行预测任务 =====");

        // 检查是否已有任务在运行
        if (!taskLock.tryLock()) {
            log.warn("前一次预测任务尚未完成，跳过本次执行");
            return;
        }

        try {
            // 1. 加载测试数据
            List<Map<String, String>> rawData = dataLoaderService.loadTestData();
            if (rawData.isEmpty()) {
                log.info("没有数据需要预测");
                return;
            }

            // 2. 特征预处理
            float[][] features = preprocessorService.preprocess(rawData);

            // 3. 模型预测
            double[] predictions = predictionService.predict(features);

            // 4. 处理预测结果
            processResults(rawData, predictions);

        } catch (Exception e) {
            log.error("预测任务执行失败", e);
        } finally {
            taskLock.unlock();
        }

        log.info("===== 预测任务执行完成 =====");
    }

    private void processResults(List<Map<String, String>> rawData, double[] predictions) {
        List<PredictionResult> results = new ArrayList<>();

        for (int i = 0; i < rawData.size() && i < predictions.length; i++) {
            String sampleId = rawData.get(i).get("sampleId");
            results.add(new PredictionResult(
                    sampleId,
                    rawData.get(i).toString(),
                    predictions[i],
                    LocalDateTime.now()
            ));
        }

        // 输出预测结果
        log.info("预测结果:");
        for (int i = 0; i < results.size(); i++) {
            log.info("样本ID: {}, CTR预测值: {}", i + 1, results.get(i).getCtrProbabilityFormatted());
        }

        // 可以在这里添加结果存储逻辑（如写入文件或数据库）
    }
}
