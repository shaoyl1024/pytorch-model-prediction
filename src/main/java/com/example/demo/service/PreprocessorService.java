package com.example.demo.service;

import com.example.demo.model.PreprocessorParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            double numValue = value.isEmpty()? param.getMedian(): Double.parseDouble(value);
            numValue = Math.max(numValue, -0.999);

            double logValue = Math.log1p(numValue);

            return (float) ((logValue - param.getMean()) / param.getScale());
        } catch (NumberFormatException e) {
            log.warn("数值特征解析失败，使用默认值: {}", param.getMedian());
            return (float) param.getMedian();
        }
    }

    private float processCategoricalFeature(String value, PreprocessorParam.CategoricalParam param) {


        // 1. 处理空值/空白值 → 替换为 "UNK"（与 Python fillna("UNK") 一致）
        String processedValue = (value == null || value.trim().isEmpty()) ? "UNK" : value.trim();

        // 2. 高频值过滤 → 不在 highFreq 中的值 → 替换为 "UNK"（与 Python 低频归并一致）
        List<String> highFreqList = param.getHighFreqValues();
        if (highFreqList != null && !highFreqList.isEmpty()) {
            Set<String> highFreqSet = new HashSet<>(highFreqList);
            if (!highFreqSet.contains(processedValue)) {
                processedValue = "UNK";
                log.warn("低频值归并 | 原始值: {}, 归并为: UNK", value);
            }
        }

        // 3. 编码映射 → 未找到时返回 defaultCode=0（与业务定义一致）
        Map<String, Integer> codeMap = param.getCodeMap();
        if (codeMap == null) {
            log.warn("codeMap 为 null，返回默认编码: 0");
            return -1f; // 无编码表时返回0
        }

        // 从 codeMap 取编码，未找到则返回 defaultCode=0（核心调整点）
        Integer code = codeMap.get(processedValue);
        if (code == null) {
            log.debug("编码未找到 | 处理后值: {}, 返回默认编码: 0", processedValue);
            return -1f;
        }

        return code.floatValue();
    }
}
