package com.example.demo.preprocessor;

import com.example.demo.model.PreprocessorParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 基于PreprocessorParam的预处理器（子类2）：实现从配置类获取参数
 */
@Service("configBasedPreprocessor")
@Slf4j
@RequiredArgsConstructor
public class ConfigBasedPreprocessor extends AbstractPreprocessorService {
    // -------------------------- 子类私有依赖（注入的配置） --------------------------
    private final PreprocessorParam preprocessorParam; // 外部注入的配置类
    private List<String> numCols; // 缓存数值列（避免重复获取）
    private List<String> catCols; // 缓存类别列

    // -------------------------- 实现父类抽象方法 --------------------------
    @Override
    public void initParams() {
        log.info("Initializing config-based preprocessor");
        PreprocessorParam.FeatureConfig config = preprocessorParam.getConfig();
        if (config == null) {
            throw new RuntimeException("FeatureConfig is null");
        }

        // 子类独有：从配置类提取特征列
        this.numCols = config.getNumCols();
        this.catCols = config.getCatCols();

        // 子类独有：验证配置完整性
        validateConfigParams();
        log.info("Config-based preprocessor initialized successfully");
    }

    @Override
    protected List<String> getNumericColumns() {
        return numCols; // 从配置类获取的数值列
    }

    @Override
    protected List<String> getCategoricalColumns() {
        return catCols; // 从配置类获取的类别列
    }

    @Override
    protected PreprocessorParam.NumericParam getNumericParam(String numericCol) {
        // 子类实现：从PreprocessorParam提取DTO
        if (!numCols.contains(numericCol)) {
            log.warn("Invalid numeric column: {}", numericCol);
            return new PreprocessorParam.NumericParam(0.0, 0.0, 1.0);
        }

        PreprocessorParam.NumericParam numParam = preprocessorParam.getNumericParams().get(numericCol);
        if (numParam == null) {
            log.warn("No numeric param for column: {}", numericCol);
            return new PreprocessorParam.NumericParam(0.0, 0.0, 1.0);
        }

        double median = numParam.getMedian() == null ? 0.0 : numParam.getMedian();
        double mean = numParam.getMean() == null ? 0.0 : numParam.getMean();
        double scale = numParam.getScale() == null ? 1.0 : numParam.getScale();
        return new PreprocessorParam.NumericParam(median, mean, scale);
    }

    @Override
    protected PreprocessorParam.CategoricalParam getCategoricalParam(String categoricalCol) {
        // 子类实现：从PreprocessorParam提取DTO
        if (!catCols.contains(categoricalCol)) {
            log.warn("Invalid categorical column: {}", categoricalCol);
            return new PreprocessorParam.CategoricalParam(Collections.emptySet(), Collections.emptyMap(), -1);
        }

        PreprocessorParam.CategoricalParam catParam = preprocessorParam.getCategoricalParams().get(categoricalCol);
        if (catParam == null) {
            log.warn("No categorical param for column: {}", categoricalCol);
            return new PreprocessorParam.CategoricalParam(Collections.emptySet(), Collections.emptyMap(), -1);
        }

        Set<String> highFreqSet = new HashSet<>(catParam.getHighFreqValues());
        Map<String, Integer> codeMap = catParam.getCodeMap() == null ? Collections.emptyMap() : catParam.getCodeMap();
        int defaultCode = -1; // 子类自定义默认编码
        return new PreprocessorParam.CategoricalParam(highFreqSet, codeMap, defaultCode);
    }

    @Override
    protected void validateSample(Map<String, String> rawSample) {
        // 子类实现：宽松验证（仅警告，不抛异常）
        for (String numCol : numCols) {
            if (!rawSample.containsKey(numCol)) {
                log.warn("Missing numeric feature: {}, use empty string", numCol);
            }
        }
        for (String catCol : catCols) {
            if (!rawSample.containsKey(catCol)) {
                log.warn("Missing categorical feature: {}, use empty string", catCol);
            }
        }
    }

    // -------------------------- 子类独有工具方法 --------------------------
    private void validateConfigParams() {
        if (numCols == null || numCols.isEmpty()) {
            throw new RuntimeException("Numeric columns are empty");
        }
        if (catCols == null || catCols.isEmpty()) {
            throw new RuntimeException("Categorical columns are empty");
        }
        if (preprocessorParam.getNumericParams().size() != numCols.size()) {
            log.warn("Numeric param count mismatch: {} vs {}", preprocessorParam.getNumericParams().size(), numCols.size());
        }
    }

    /**
     * 数值特征处理（公共逻辑：缺失值→Log1p→标准化）
     */
    @Override
    protected float processNumericFeature(String value, String numericCol) {
        PreprocessorParam.NumericParam param = getNumericParam(numericCol);
        if (param == null) {
            log.error("Numeric param DTO is null for column: {}", numericCol);
            return 0.0f;
        }

        try {
            double numValue = value.isEmpty()? param.getMean(): Double.parseDouble(value);
            numValue = Math.max(numValue, -0.999);
            double logValue = Math.log1p(numValue);

            // 1. 用BigDecimal将结果四舍五入到6位小数
            double rawResult = (logValue - param.getMedian()) / param.getScale();
            BigDecimal roundedResult = new BigDecimal(rawResult)
                    .setScale(6, RoundingMode.HALF_UP); // 保留6位小数，四舍五入

            // 2. 转为float（此时仅保留6位小数的精度，符合预期）
            return roundedResult.floatValue();
        } catch (NumberFormatException e) {
            log.warn("数值特征解析失败，使用默认值: {}", param.getMedian());
            return  param.getMedian().floatValue();
        }
    }


    /**
     * 类别特征处理（公共逻辑：空值→UNK→低频过滤→编码）
     */
    protected int processCategoricalFeature(String rawVal, String categoricalCol) {
        PreprocessorParam.CategoricalParam paramDTO = getCategoricalParam(categoricalCol);
        if (paramDTO == null) {
            log.error("Categorical param DTO is null for column: {}", categoricalCol);
            return paramDTO.getDefaultCode();
        }

        // 1. 空值/空白值→UNK
        String processedVal = (rawVal == null || rawVal.trim().isEmpty())
                ? UNK_MARKER
                : rawVal.trim();

        // 2. 低频值过滤→UNK
        Set<String> highFreqSet = paramDTO.getHighFreqValues();
        if (highFreqSet != null && !highFreqSet.isEmpty() && !highFreqSet.contains(processedVal)) {
            log.debug("Low-frequency value '{}' for column {} replaced with UNK", processedVal, categoricalCol);
            processedVal = UNK_MARKER;
        }

        // 3. 标签编码（未找到用默认值）
        Map<String, Integer> codeMap = paramDTO.getCodeMap();
        Integer code = (codeMap != null) ? codeMap.get(processedVal) : null;
        if (code == null) {
            log.debug("Value '{}' for column {} not in codeMap, use default code: {}", processedVal, categoricalCol, paramDTO.getDefaultCode());
            code = paramDTO.getDefaultCode();
        }

        return code;
    }
}
