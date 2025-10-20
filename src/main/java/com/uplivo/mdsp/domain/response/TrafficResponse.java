package com.uplivo.mdsp.domain.response;

import lombok.Builder;
import lombok.Data;

/**
 * @Description 流量特征打分结果
 * @Author charles
 * @Date 2025/9/8 11:50
 * @Version 1.0.0
 */
@Data
@Builder
public class TrafficResponse {
    private String model;
    private String version;
    private String requestId;
    private float[] scores;
}
