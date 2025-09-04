package com.example.demo.config;

import com.example.demo.model.PreprocessorParam;
import com.example.demo.exception.ModelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class PreprocessorConfig {

    @Value("${preprocessor.config-path}")
    private Resource preprocessorResource;

    @Getter
    private PreprocessorParam preprocessorParam;

    @Bean
    public PreprocessorParam preprocessorParam() {
        try (InputStream is = preprocessorResource.getInputStream()) {
            ObjectMapper objectMapper = new ObjectMapper();
            preprocessorParam = objectMapper.readValue(is, PreprocessorParam.class);
            log.info("成功加载预处理配置");
            return preprocessorParam;
        } catch (IOException e) {
            log.error("加载预处理配置失败", e);
            return null;
        }
    }
}
