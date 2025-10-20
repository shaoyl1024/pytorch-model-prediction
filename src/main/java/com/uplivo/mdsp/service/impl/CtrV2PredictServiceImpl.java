package com.uplivo.mdsp.service.impl;

import ai.onnxruntime.OrtEnvironment;
import com.uplivo.mdsp.config.model.OnnxModelConfig;
import com.uplivo.mdsp.core.preprocessor.deepfm.base.AbstractPreprocessor;
import com.uplivo.mdsp.service.AbstractModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @Description CTR v2模型服务实现类
 * @Author charles
 * @Date 2025/9/10 17:47
 * @Version 1.0.0
 */
@Service("ctrV2")
@Slf4j
public class CtrV2PredictServiceImpl extends AbstractModelService {

    private final AbstractPreprocessor preprocessor;

    // 子类构造函数：必须传递父类所需的两个参数
    public CtrV2PredictServiceImpl(
            @Qualifier("ctrV2Preprocessor") AbstractPreprocessor preprocessor,
            OrtEnvironment ortEnvironment,
            OnnxModelConfig onnxConfig
    ) {
        super(ortEnvironment, onnxConfig); // 传递给父类
        this.preprocessor = preprocessor;
    }

    /**
     * 实现父类抽象方法：提供CTR v4模型的预处理器
     */
    @Override
    protected AbstractPreprocessor getPreprocessor() {
        return preprocessor;
    }

    /**
     * 实现父类抽象方法：提供CTR v4模型的版本标识
     */
    @Override
    protected String getModelVersion() {
        return "ctr_v2";
    }

}
