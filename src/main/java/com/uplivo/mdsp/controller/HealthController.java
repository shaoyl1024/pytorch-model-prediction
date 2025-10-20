package com.uplivo.mdsp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description 服务健康检查控制器
 * @Author charles
 * @Date 2025/9/8 16:20
 * @Version 1.0.0
 */
@RestController
@Slf4j
@RequestMapping("/health")
public class HealthController {

    @ResponseBody
    @GetMapping(value = "/ping")
    public String healthCheck() {
        return "success";
    }
}
