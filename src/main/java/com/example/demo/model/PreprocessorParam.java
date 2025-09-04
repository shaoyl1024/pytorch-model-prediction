package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PreprocessorParam {
    @JsonProperty("num_params")
    private Map<String, NumericParam> numericParams;

    @JsonProperty("cat_params")
    private Map<String, CategoricalParam> categoricalParams;
    private FeatureConfig config;

    @Data
    public static class NumericParam {
        private double mean;
        private double median;
        private double scale;
    }

    @Data
    public static class CategoricalParam {
        @JsonProperty("high_freq")
        private List<String> highFreqValues;
        @JsonProperty("code_map")
        private Map<String, Integer> codeMap;
        private int defaultCode;
    }

    @Data
    public static class FeatureConfig {
        @JsonProperty("num_cols")
        private List<String> numCols;
        @JsonProperty("cat_cols")
        private List<String> catCols;
    }
}
