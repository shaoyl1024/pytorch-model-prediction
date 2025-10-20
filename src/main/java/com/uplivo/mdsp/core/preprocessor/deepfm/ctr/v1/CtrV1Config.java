package com.uplivo.mdsp.core.preprocessor.deepfm.ctr.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplivo.mdsp.config.properties.ModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * @Description 预处理配置类
 * @Author charles
 * @Date 2025/9/6 00:28
 * @Version 1.0.0
 */
@Configuration
@Slf4j
public class CtrV1Config {

    @Value("${model.configs.ctr_v1.preprocessor-path}")
    private Resource preprocessorResource;

    @Bean(name = "ctrV1Param")
    public CtrV1Param preprocessorParam() {
        try (InputStream is = preprocessorResource.getInputStream()) {
            ObjectMapper objectMapper = new ObjectMapper();
            CtrV1Param param = objectMapper.readValue(is, CtrV1Param.class);

            log.info("Preprocessing configuration loaded successfully. Number of numeric features: {}, number of categorical features: {}",
                    param.getConfig().getNumCols().size(),
                    param.getConfig().getCatCols().size());

            return param;
        } catch (IOException e) {
            log.error("Failed to load preprocessing configuration. File path: {}", preprocessorResource, e);
            throw new RuntimeException("Preprocessing configuration file loading failed", e);
        }
    }
}