package com.example.demo.service;

import com.example.demo.preprocessor.AbstractPreprocessorService;
import org.python.modules._json._json;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class ModelService {
    // 注入Pickle预处理器（用于模型1）
    private final AbstractPreprocessorService picklePreprocessor;

    private final AbstractPreprocessorService jsonPreprocessor;


    // 构造器注入，用@Qualifier区分子类
    public ModelService(
            @Qualifier("pickleBasedPreprocessor") AbstractPreprocessorService picklePreprocessor,
            @Qualifier("configBasedPreprocessor") AbstractPreprocessorService jsonPreprocessor
    ) {
        this.picklePreprocessor = picklePreprocessor;
        this.jsonPreprocessor = jsonPreprocessor;
    }

    // 模型1：使用Pickle预处理器
    public float[][] predictModel1(List<Map<String, String>> rawData) {
        float[][] features = picklePreprocessor.batchPreprocess(rawData);
        return features;
    }

    // 模型1：使用Pickle预处理器
    public float[][] predictModel2(List<Map<String, String>> rawData) {
        float[][] features = jsonPreprocessor.batchPreprocess(rawData);
        return features;
    }
}
