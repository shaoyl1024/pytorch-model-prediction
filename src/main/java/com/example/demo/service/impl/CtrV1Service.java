package com.example.demo.service.impl;

import com.example.demo.preprocessor.AbstractPreprocessor;
import com.example.demo.service.AbstractModelService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @Description CTR v1模型服务实现类
 * @Author charles
 * @Date 2025/9/5 23:50
 * @Version 1.0.0
 */
@Service("ctrV1")
public class CtrV1Service extends AbstractModelService {

    private final AbstractPreprocessor preprocessor;

    public CtrV1Service(@Qualifier("ctrV1Preprocessor") AbstractPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    /**
     * 实现父类抽象方法：提供CTR v1模型的预处理器
     */
    @Override
    protected AbstractPreprocessor getPreprocessor() {
        return preprocessor;
    }

    /**
     * 实现父类抽象方法：提供CTR v1模型的版本标识
     */
    @Override
    protected String getModelVersion() {
        return "v1";
    }
}
