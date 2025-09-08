package com.example.demo.config;

import ai.onnxruntime.OrtEnvironment;
import com.example.demo.exception.ModelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Description ONNX Runtime全局环境配置类
 * @Author charles
 * @Date 2025/9/8 16:33
 * @Version 1.0.0
 */
@Configuration
@Slf4j
public class OrtEnvironmentConfig {

    /**
     * 创建ONNX全局环境（单例）
     * <p>环境是ONNX Runtime的核心，管理线程池、内存分配等全局资源，所有模型共享一个环境
     *
     * @return 全局ONNX环境实例
     * @throws ModelException 环境创建失败时抛出业务异常
     */
    @Bean(destroyMethod = "close")
    public OrtEnvironment ortEnvironment() {
        try {
            return OrtEnvironment.getEnvironment();
        } catch (Exception e) {
            log.error("Failed to create ONNX environment", e);
            throw new ModelException("Failed to create ONNX environment", e);
        }
    }

}
