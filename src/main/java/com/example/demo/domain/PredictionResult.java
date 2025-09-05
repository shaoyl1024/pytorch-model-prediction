package com.example.demo.domain;

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
    private String sampleId;
    private String rawFeatures;
    private double ctrProbability;
    private LocalDateTime predictionTime;

    public String getCtrProbabilityFormatted() {
        if (ctrProbability <= 0 || Double.isNaN(ctrProbability) || Double.isInfinite(ctrProbability)) {
            return "0.0000";
        }
        DecimalFormat df = new DecimalFormat("0.0000");
        return df.format(ctrProbability);
    }
}
