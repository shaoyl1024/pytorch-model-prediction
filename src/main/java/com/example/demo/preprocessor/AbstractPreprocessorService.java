package com.example.demo.preprocessor;

import com.example.demo.model.PreprocessorParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description 抽象预处理器父类：实现所有公共逻辑，差异化逻辑留待子类实现
 * @Author charles
 * @Date 2025/9/5 14:11
 * @Version 1.0.0
 */
@Component
@Slf4j
public abstract class AbstractPreprocessorService {

    // -------------------------- 公共常量（所有子类共享） --------------------------
    protected static final String UNK_MARKER = "UNK"; // 未知值标记
    protected static final double LOG1P_LOWER_BOUND = -0.999; // Log1p下界
    protected static final double MIN_SCALE = 1e-9; // 避免标准化除零的最小标准差

    // -------------------------- 抽象方法（子类必须实现的差异化逻辑） --------------------------

    /**
     * 1. 参数初始化（子类实现：如加载Pickle / 解析配置类）
     *
     * @PostConstruct 确保子类初始化时自动执行
     */
    @PostConstruct
    public abstract void initParams();

    /**
     * 2. 获取数值特征列列表（子类自定义列名）
     */
    protected abstract List<String> getNumericColumns();

    /**
     * 3. 获取类别特征列列表（子类自定义列名）
     */
    protected abstract List<String> getCategoricalColumns();

    /**
     * 4. 根据数值列名获取参数DTO（子类实现参数来源）
     */
    protected abstract PreprocessorParam.NumericParam getNumericParam(String numericCol);

    /**
     * 5. 根据类别列名获取参数DTO（子类实现参数来源）
     */
    protected abstract PreprocessorParam.CategoricalParam getCategoricalParam(String categoricalCol);

    /**
     * 6. 验证单样本是否含必要特征（子类自定义验证规则）
     */
    protected abstract void validateSample(Map<String, String> rawSample);

    protected abstract float processNumericFeature(String rawVal, String numericCol);

    protected abstract int processCategoricalFeature(String rawVal, String categoricalCol);

    // -------------------------- 公共方法（父类实现，子类直接复用） --------------------------

    /**
     * 批量预处理（公共逻辑：循环调用单样本处理）
     */
    public float[][] batchPreprocess(List<Map<String, String>> rawSamples) {
        if (rawSamples == null || rawSamples.isEmpty()) {
            log.info("Batch preprocessing: empty raw samples, return empty array");
            return new float[0][0];
        }

        List<String> numCols = getNumericColumns();
        List<String> catCols = getCategoricalColumns();
        int featureDim = numCols.size() + catCols.size();
        float[][] batchFeatures = new float[rawSamples.size()][featureDim];

        // 循环处理每个样本（公共逻辑，无需子类重复写）
        for (int i = 0; i < rawSamples.size(); i++) {
            batchFeatures[i] = singlePreprocess(rawSamples.get(i));
        }

        log.info("Batch preprocessing completed: {} features (dim: {})", batchFeatures.length, featureDim);
        return batchFeatures;
    }

    /**
     * 单样本预处理（公共骨架：验证→数值处理→类别处理）
     */
    public float[] singlePreprocess(Map<String, String> rawSample) {
        // 1. 验证样本（子类实现规则）
        validateSample(rawSample);
        List<String> numCols = getNumericColumns();
        List<String> catCols = getCategoricalColumns();
        int featureDim = numCols.size() + catCols.size();
        float[] processedFeatures = new float[featureDim];
        int featureIndex = 0;

        // 2. 处理数值特征（公共逻辑）
        for (String numCol : numCols) {
            String rawVal = rawSample.getOrDefault(numCol, "");
            processedFeatures[featureIndex++] = processNumericFeature(rawVal, numCol);
        }

        // 3. 处理类别特征（公共逻辑）
        for (String catCol : catCols) {
            String rawVal = rawSample.getOrDefault(catCol, "");
            processedFeatures[featureIndex++] = processCategoricalFeature(rawVal, catCol);
        }

        return processedFeatures;
    }

}
