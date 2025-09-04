package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.text.DecimalFormat;
import java.time.LocalDateTime;

/**
 * @Description 预测结果模型：包含样本ID、特征、点击概率、预测时间
 * @Author charles
 * @Date 2025/9/3 20:51
 * @Version 1.0.0
 */
@Data
@AllArgsConstructor
public class PredictionResult {
    // 样本唯一标识（可按测试数据行号生成）
    private String sampleId;
    // 原始特征（简化展示，可按需扩展）
    private String rawFeatures;
    // ONNX模型输出的点击概率（0~1）
    private double ctrProbability;
    // 预测时间（定时任务执行时间）
    private LocalDateTime predictionTime;

    public String getCtrProbabilityFormatted() {
        // 1. 处理 null/异常值
        if (ctrProbability <= 0 || Double.isNaN(ctrProbability) || Double.isInfinite(ctrProbability)) {
            return "0.0000";
        }
        // 2. 保留4位小数（DecimalFormat 比占位符更灵活，支持末尾补0）
        DecimalFormat df = new DecimalFormat("0.0000");
        return df.format(ctrProbability);
    }
}
