package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class PreprocessorParam {

    @JsonProperty("num_params")
    private Map<String, NumericParam> numericParams;

    @JsonProperty("cat_params")
    private Map<String, CategoricalParam> categoricalParams;

    private FeatureConfig config;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor  // 新增无参构造函数，允许Jackson反序列化
    public static class NumericParam {
        private Double mean;
        private Double median;
        private Double scale;
    }

    @Data
    @NoArgsConstructor  // 关键：添加无参构造函数（因JSON无defaultCode字段）
    @AllArgsConstructor // 保留全参构造，方便手动创建对象
    public static class CategoricalParam {
        @JsonProperty("high_freq")
        private Set<String> highFreqValues;

        @JsonProperty("code_map")
        private Map<String, Integer> codeMap;

        // JSON中无此字段，反序列化时会保持默认值（-1）
        private int defaultCode = -1;  // 显式设置默认值，避免null
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor  // 新增无参构造函数
    public static class FeatureConfig {
        @JsonProperty("num_cols")
        private List<String> numCols;

        @JsonProperty("cat_cols")
        private List<String> catCols;
    }
}