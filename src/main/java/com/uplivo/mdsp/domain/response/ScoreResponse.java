package com.uplivo.mdsp.domain.response;

import lombok.Builder;
import lombok.Data;

/**
 * @Description 样本打分结果
 * @Author charles
 * @Date 2025/9/8 11:50
 * @Version 1.0.0
 */
@Data
@Builder
public class ScoreResponse {
    private String requestId;
    private float[] scores;
}
