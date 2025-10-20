package com.uplivo.mdsp.service.impl;

import ai.onnxruntime.OrtEnvironment;
import com.uplivo.mdsp.config.model.ModelConfigManager;
import com.uplivo.mdsp.core.preprocessor.deepfm.base.AbstractPreprocessor;
import com.uplivo.mdsp.service.AbstractModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @Description CTR v1模型服务实现类
 * @Author charles
 * @Date 2025/9/8 17:47
 * @Version 1.0.0
 */
@Service("ctrV1")
@Slf4j
public class CtrV1PredictImpl extends AbstractModelService {

    private final AbstractPreprocessor preprocessor;

    // 子类构造函数：必须传递父类所需的两个参数
    public CtrV1PredictImpl(
            @Qualifier("ctrV1Preprocessor") AbstractPreprocessor preprocessor,
            OrtEnvironment ortEnvironment,
            ModelConfigManager modelConfigManager
    ) {
        super(ortEnvironment, modelConfigManager); // 传递给父类
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
        return "ctr_v1";
    }

}
