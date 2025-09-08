package com.example.demo.controller;

import com.alibaba.fastjson2.JSONObject;
import com.example.demo.domain.TrafficRequest;
import com.example.demo.domain.TrafficResponse;
import com.example.demo.service.AbstractModelService;
import com.example.demo.service.ModelServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description 模型服务RPC接口控制器，提供RESTful API作为RPC服务
 * @Author charles
 * @Date 2025/9/8 11:20
 * @Version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/model")
public class PredictController {

    private final ModelServiceFactory modelFactory;

    public PredictController(ModelServiceFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    /**
     * 单条特征打分接口
     */
    @PostMapping("/predict")
    public ResponseEntity<TrafficResponse> predict(@RequestBody TrafficRequest request) {
        try {
            long startTime = System.currentTimeMillis();
            String model = request.getModel();
            String version = request.getVersion();
            log.info("Start prediction - model: {}, version: {}, requestId: {}", model, version, request.getRequestId());

            AbstractModelService modelService = modelFactory.getServiceByVersion(model + "_" + version);

            float[] scores = modelService.predict(request.getFeatures());

            TrafficResponse trafficScoreResponse = TrafficResponse.builder()
                    .model(request.getModel()).version(request.getVersion())
                    .requestId(request.getRequestId()).scores(scores)
                    .build();

            log.info("Prediction completed in {}ms, requestId: {}, scores: {}", System.currentTimeMillis() - startTime,
                    request.getRequestId(), JSONObject.toJSONString(trafficScoreResponse));

            return ResponseEntity.ok(trafficScoreResponse);

        } catch (Exception e) {
            log.error("Prediction failed", e);
        }
        return null;
    }
}
