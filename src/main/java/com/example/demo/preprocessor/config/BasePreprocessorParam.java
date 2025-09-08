package com.example.demo.preprocessor.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description 公共的预处理参数
 * @Author charles
 * @Date 2025/9/8 17:56
 * @Version 1.0.0
 */
@Data
public class BasePreprocessorParam {

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
