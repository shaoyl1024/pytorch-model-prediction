package com.example.demo.service;

import com.example.demo.model.PreprocessorParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PreprocessorService {

    private final PreprocessorParam preprocessorParam;

    public float[][] preprocess(List<Map<String, String>> rawData) {
        if (rawData.isEmpty()) {
            return new float[0][0];
        }

        PreprocessorParam.FeatureConfig config = preprocessorParam.getConfig();
        List<String> numCols = config.getNumCols();
        List<String> catCols = config.getCatCols();
        int featureDim = numCols.size() + catCols.size();

        float[][] features = new float[rawData.size()][featureDim];

        for (int i = 0; i < rawData.size(); i++) {
            Map<String, String> record = rawData.get(i);
            features[i] = processSingleRecord(record, numCols, catCols);
        }

        return features;
    }

    private float[] processSingleRecord(Map<String, String> record, List<String> numCols, List<String> catCols) {
        float[] features = new float[numCols.size() + catCols.size()];
        int index = 0;

        // 处理数值型特征
        for (String col : numCols) {
            String value = record.getOrDefault(col, "");
            PreprocessorParam.NumericParam param = preprocessorParam.getNumericParams().get(col);
            features[index++] = processNumericFeature(value, param);
        }

        // 处理分类型特征
        for (String col : catCols) {
            String value = record.getOrDefault(col, "");
            PreprocessorParam.CategoricalParam param = preprocessorParam.getCategoricalParams().get(col);
            features[index++] = processCategoricalFeature(value, param);
        }

        return features;
    }

    private float processNumericFeature(String value, PreprocessorParam.NumericParam param) {
        try {
            if (value.isEmpty()) {
                return (float) param.getMedian();
            }
            double numValue = Double.parseDouble(value);
            // 标准化
            return (float) ((numValue - param.getMean()) / param.getScale());
        } catch (NumberFormatException e) {
            log.warn("数值特征解析失败，使用默认值: {}", param.getMedian());
            return (float) param.getMedian();
        }
    }

    private float processCategoricalFeature(String value, PreprocessorParam.CategoricalParam param) {
        if (value.isEmpty() || !param.getCodeMap().containsKey(value)) {
            return param.getDefaultCode();
        }
        return param.getCodeMap().get(value);
    }
}
