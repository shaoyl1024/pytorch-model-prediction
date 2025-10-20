package com.uplivo.mdsp.domain.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @Description 服务接收流量特征参数
 * @Author charles
 * @Date 2025/9/8 11:50
 * @Version 1.0.0
 */
@Data
public class TrafficRequest {
    private String model;
    private String version;
    private String requestId;
    private List<Map<String, String>> features;
}
