package com.example.demo.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description 预处理参数配置
 * @Author charles
 * @Date 2025/9/3 20:51
 * @Version 1.0.0
 */
@Data
public class PreprocessorParam {

    @JsonProperty("num_params")
    private Map<String, NumericParam> numericParams;

    @JsonProperty("cat_params")
    private Map<String, CategoricalParam> categoricalParams;

    private FeatureConfig config;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NumericParam {
        private Double mean;
        private Double median;
        private Double scale;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoricalParam {
        @JsonProperty("high_freq")
        private Set<String> highFreqValues;

        @JsonProperty("code_map")
        private Map<String, Integer> codeMap;

        private int defaultCode = -1;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeatureConfig {
        @JsonProperty("num_cols")
        private List<String> numCols;

        @JsonProperty("cat_cols")
        private List<String> catCols;
    }
}