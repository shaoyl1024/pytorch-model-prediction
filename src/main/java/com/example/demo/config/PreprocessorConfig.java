package com.example.demo.config;

import com.example.demo.model.PreprocessorParam;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Value("${model.ctr_v2.preprocessor-path}")
    private Resource preprocessorResource;

    @Bean
    public PreprocessorParam preprocessorParam() {
        try (InputStream is = preprocessorResource.getInputStream()) {
            ObjectMapper objectMapper = new ObjectMapper();
            PreprocessorParam param = objectMapper.readValue(is, PreprocessorParam.class);
            log.info("成功加载预处理配置，包含{}个数值特征和{}个分类特征",
                    param.getConfig().getNumCols().size(),
                    param.getConfig().getCatCols().size());
            return param;
        } catch (IOException e) {
            log.error("加载预处理配置失败，文件路径：{}", preprocessorResource, e);
            throw new RuntimeException("预处理配置文件加载失败", e);
        }
    }
}