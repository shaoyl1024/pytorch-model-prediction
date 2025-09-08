package com.example.demo.service.impl;

import ai.onnxruntime.OrtEnvironment;
import com.example.demo.config.OnnxModelConfig;
import com.example.demo.preprocessor.AbstractPreprocessor;
import com.example.demo.service.AbstractModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @Description CTR v3模型服务实现类
 * @Author charles
 * @Date 2025/9/8 17:47
 * @Version 1.0.0
 */
@Service("ctrV3")
@Slf4j
public class CtrV3PredictServiceImpl extends AbstractModelService {

    private final AbstractPreprocessor preprocessor;

    // 子类构造函数：必须传递父类所需的两个参数
    public CtrV3PredictServiceImpl(
            @Qualifier("ctrV3Preprocessor") AbstractPreprocessor preprocessor,
            OrtEnvironment ortEnvironment,
            OnnxModelConfig onnxConfig
    ) {
        super(ortEnvironment, onnxConfig); // 传递给父类
        this.preprocessor = preprocessor;
    }

    /**
     * 实现父类抽象方法：提供CTR v3模型的预处理器
     */
    @Override
    protected AbstractPreprocessor getPreprocessor() {
        return preprocessor;
    }

    /**
     * 实现父类抽象方法：提供CTR v3模型的版本标识
     */
    @Override
    protected String getModelVersion() {
        return "ctr_v3";
    }

}
