package com.example.demo.service.impl;

import ai.onnxruntime.OrtEnvironment;
import com.example.demo.config.OnnxModelConfig;
import com.example.demo.preprocessor.AbstractPreprocessor;
import com.example.demo.service.AbstractModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @Description CTR v1模型服务实现类
 * @Author charles
 * @Date 2025/9/5 23:50
 * @Version 1.0.0
 */
@Service("ctrV1")
@Slf4j
public class CtrV1PredictServiceImpl extends AbstractModelService {

    private final AbstractPreprocessor preprocessor;

    public CtrV1PredictServiceImpl(
            @Qualifier("ctrV1Preprocessor") AbstractPreprocessor preprocessor,
            OrtEnvironment ortEnvironment,
            OnnxModelConfig onnxConfig
    ) {
        super(ortEnvironment, onnxConfig); // 传递给父类
        this.preprocessor = preprocessor;
    }


    @Override
    protected AbstractPreprocessor getPreprocessor() {
        return preprocessor;
    }

    /**
     * 实现父类抽象方法：提供CTR v1模型的版本标识
     */
    @Override
    protected String getModelVersion() {
        return "ctr_v1";
    }

}
