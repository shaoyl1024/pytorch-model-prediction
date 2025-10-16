package com.example.demo.preprocessor.impl;

import com.example.demo.exception.ModelException;
import com.example.demo.preprocessor.AbstractPreprocessor;
import com.example.demo.preprocessor.config.ctrv1.CtrV1PreprocessorParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * @Description 基于配置类的CTR v1预处理器
 * @Author charles
 * @Date 2025/9/6 01:20
 * @Version 1.0.0
 */
@Service("ctrV1Preprocessor")
@Slf4j
@RequiredArgsConstructor
public class CTRV1PreprocessorImpl extends AbstractPreprocessor {

    // ============================================================================
    // 依赖与缓存：外部注入的配置类及本地缓存的特征列（避免重复解析）
    // ============================================================================
    /** 预处理配置类（由PreprocessorConfig注入，包含数值/分类特征的所有规则） */
    private final CtrV1PreprocessorParam preprocessorParam;
    /** 缓存的数值特征列名（初始化时从配置类提取，避免重复调用getConfig()） */
    private List<String> numCols;
    /** 缓存的分类特征列名（初始化时从配置类提取，避免重复调用getConfig()） */
    private List<String> catCols;


    // ============================================================================
    // 抽象方法实现：父类定义的差异化逻辑（核心预处理流程定制）
    // ============================================================================
    /**
     * 初始化预处理参数（Spring生命周期方法）
     * 流程：1. 从配置类提取特征列定义 2. 校验配置完整性 3. 缓存特征列到本地变量
     * @throws ModelException 配置缺失/无效时抛出（终止预处理流程）
     */
    @Override
    public void initParams() {
        log.info("Initializing CTR v1 preprocessor from config");
        CtrV1PreprocessorParam.FeatureConfig featureConfig = preprocessorParam.getConfig();

        // 校验核心配置非空
        if (featureConfig == null) {
            throw new ModelException("CTR v1: FeatureConfig is null in preprocessor param", "NULL_FEATURE_CONFIG");
        }

        // 提取并缓存特征列（转为不可修改列表，避免意外篡改）
        this.numCols = Collections.unmodifiableList(featureConfig.getNumCols());
        this.catCols = Collections.unmodifiableList(featureConfig.getCatCols());

        // 校验配置完整性（确保特征列和参数匹配）
        validateConfigParams();
        log.info("CTR v1 preprocessor initialized successfully | Numeric cols: {}, Categorical cols: {}",
                numCols.size(), catCols.size());
    }

    /**
     * 获取CTR v1的数值特征列名（实现父类抽象方法）
     * @return 初始化时缓存的数值特征列列表（不可修改）
     */
    @Override
    protected List<String> getNumericColumns() {
        return numCols;
    }

    /**
     * 获取CTR v1的分类特征列名（实现父类抽象方法）
     * @return 初始化时缓存的分类特征列列表（不可修改）
     */
    @Override
    protected List<String> getCategoricalColumns() {
        return catCols;
    }

    /**
     * 根据数值特征列名获取预处理参数DTO（实现父类抽象方法）
     * 从PreprocessorParam中提取对应列的均值、中位数、标准差，封装为统一DTO
     * @param numericCol 数值特征列名（如"I1"）
     * @return 数值特征预处理参数DTO（无匹配列时返回默认参数，避免空指针）
     */
    @Override
    protected CtrV1PreprocessorParam.NumericParam getNumericParam(String numericCol) {
        // 校验列名合法性
        if (!numCols.contains(numericCol)) {
            log.warn("CTR v1: Invalid numeric column '{}' (not in config list)", numericCol);
            return new CtrV1PreprocessorParam.NumericParam(0.0, 0.0, 1.0); // 默认参数：无标准化效果
        }

        // 从配置类获取参数（处理空值，避免NPE）
        CtrV1PreprocessorParam.NumericParam rawParam = preprocessorParam.getNumericParams().get(numericCol);
        if (rawParam == null) {
            log.warn("CTR v1: No numeric param found for column '{}', use default", numericCol);
            return new CtrV1PreprocessorParam.NumericParam(0.0, 0.0, 1.0);
        }

        // 空安全处理：参数为null时用默认值
        double median = rawParam.getMedian() != null ? rawParam.getMedian() : 0.0;
        double mean = rawParam.getMean() != null ? rawParam.getMean() : 0.0;
        double scale = rawParam.getScale() != null ? rawParam.getScale() : 1.0;

        return new CtrV1PreprocessorParam.NumericParam(median, mean, scale);
    }

    /**
     * 根据分类特征列名获取预处理参数DTO（实现父类抽象方法）
     * 从PreprocessorParam中提取对应列的高频值集合、编码映射，封装为统一DTO
     * @param categoricalCol 分类特征列名（如"C1"）
     * @return 分类特征预处理参数DTO（无匹配列时返回空集合，避免空指针）
     */
    @Override
    protected CtrV1PreprocessorParam.CategoricalParam getCategoricalParam(String categoricalCol) {
        // 校验列名合法性
        if (!catCols.contains(categoricalCol)) {
            log.warn("CTR v1: Invalid categorical column '{}' (not in config list)", categoricalCol);
            return new CtrV1PreprocessorParam.CategoricalParam(Collections.emptySet(), Collections.emptyMap(), -1);
        }

        // 从配置类获取参数（处理空值，避免NPE）
        CtrV1PreprocessorParam.CategoricalParam rawParam = preprocessorParam.getCategoricalParams().get(categoricalCol);
        if (rawParam == null) {
            log.warn("CTR v1: No categorical param found for column '{}', use default", categoricalCol);
            return new CtrV1PreprocessorParam.CategoricalParam(Collections.emptySet(), Collections.emptyMap(), -1);
        }

        // 空安全处理：参数为null时用默认值
        Set<String> highFreqSet = rawParam.getHighFreqValues() != null
                ? new HashSet<>(rawParam.getHighFreqValues())
                : Collections.emptySet();
        Map<String, Integer> codeMap = rawParam.getCodeMap() != null
                ? rawParam.getCodeMap()
                : Collections.emptyMap();
        int defaultCode = -1; // 未知值默认编码（与模型训练逻辑保持一致）

        return new CtrV1PreprocessorParam.CategoricalParam(highFreqSet, codeMap, defaultCode);
    }

    /**
     * 验证单条原始样本的合法性（实现父类抽象方法）
     * 宽松验证策略：缺失特征仅警告，不中断处理（兼容部分样本字段缺失场景）
     * @param rawSample 原始样本（Map键为特征名，值为原始字符串）
     */
    @Override
    protected void validateSample(Map<String, String> rawSample) {
        // 检查数值特征缺失（仅警告）
        for (String numCol : numCols) {
            if (!rawSample.containsKey(numCol)) {
                log.warn("CTR v1: Missing numeric feature '{}', will use empty value handling", numCol);
            }
        }

        // 检查分类特征缺失（仅警告）
        for (String catCol : catCols) {
            if (!rawSample.containsKey(catCol)) {
                log.warn("CTR v1: Missing categorical feature '{}', will use empty value handling", catCol);
            }
        }
    }

    /**
     * 数值特征预处理（实现父类抽象方法）
     * 流程：1. 缺失值填充（用均值） 2. Log1p转换（处理长尾分布） 3. 标准化（(x-中位数)/标准差）
     * 增强：结果四舍五入保留6位小数，避免浮点精度问题
     * @param rawVal 原始数值特征值（可能为空/非法字符串）
     * @param numericCol 数值特征列名（用于获取对应预处理参数）
     * @return 处理后的浮点型特征值（适配模型输入）
     */
    @Override
    protected float processNumericFeature(String rawVal, String numericCol) {
        CtrV1PreprocessorParam.NumericParam param = getNumericParam(numericCol);
        if (param == null) {
            log.error("CTR v1: Numeric param DTO is null for column '{}'", numericCol);
            return 0.0f;
        }

        try {
            // 1. 处理缺失值/空值：用均值填充（CTR v1特有逻辑）
            double numValue = (rawVal == null || rawVal.trim().isEmpty())
                    ? param.getMean()
                    : Double.parseDouble(rawVal.trim());

            // 2. Log1p转换：处理数值长尾分布，确保输入≥LOG1P_LOWER_BOUND（避免log1p(x)≤-1导致NaN）
            numValue = Math.max(numValue, LOG1P_LOWER_BOUND);
            double logValue = Math.log1p(numValue);

            // 3. 标准化：(log转换后的值 - 中位数) / 安全标准差（避免除零）
            double safeScale = Math.max(param.getScale(), MIN_SCALE);
            double rawResult = (logValue - param.getMedian()) / safeScale;

            // 4. 四舍五入保留6位小数（减少浮点精度差异影响）
            BigDecimal roundedResult = new BigDecimal(rawResult)
                    .setScale(6, RoundingMode.HALF_UP);

            return roundedResult.floatValue();

        } catch (NumberFormatException e) {
            // 解析失败时用中位数填充（降级策略）
            log.warn("CTR v1: Failed to parse numeric value '{}' for column '{}', use median: {}",
                    rawVal, numericCol, param.getMedian());
            return param.getMedian().floatValue();
        }
    }

    /**
     * 分类特征预处理（实现父类抽象方法）
     * 流程：1. 空值→UNK 2. 低频值→UNK 3. 编码映射（字符串→整数）
     * @param rawVal 原始分类特征值（可能为空/低频值）
     * @param categoricalCol 分类特征列名（用于获取对应预处理参数）
     * @return 处理后的整型编码（转为float存入特征数组，与数值特征类型统一）
     */
    @Override
    protected int processCategoricalFeature(String rawVal, String categoricalCol) {
        CtrV1PreprocessorParam.CategoricalParam paramDTO = getCategoricalParam(categoricalCol);
        if (paramDTO == null) {
            log.error("CTR v1: Categorical param DTO is null for column '{}'", categoricalCol);
            return -1; // 返回默认未知编码
        }

        // 1. 空值/空白值→UNK标记（统一未知值表示）
        String processedVal = (rawVal == null || rawVal.trim().isEmpty())
                ? UNK_MARKER
                : rawVal.trim();

        // 2. 低频值过滤：不在高频集合中的值→UNK（减少稀疏性，符合配置规则）
        Set<String> highFreqSet = paramDTO.getHighFreqValues();
        if (!highFreqSet.isEmpty() && !highFreqSet.contains(processedVal)) {
            log.debug("CTR v1: Low-frequency value '{}' for column '{}' replaced with UNK",
                    processedVal, categoricalCol);
            processedVal = UNK_MARKER;
        }

        // 3. 标签编码：根据配置的映射表转换，无匹配→默认编码
        Map<String, Integer> codeMap = paramDTO.getCodeMap();
        Integer code = codeMap.getOrDefault(processedVal, paramDTO.getDefaultCode());
        if (code == paramDTO.getDefaultCode()) {
            log.debug("CTR v1: Value '{}' for column '{}' not in codeMap, use default code: {}",
                    processedVal, categoricalCol, paramDTO.getDefaultCode());
        }

        return code;
    }


    // ============================================================================
    // 私有工具方法：配置校验（确保初始化的参数合法可用）
    // ============================================================================
    /**
     * 校验配置类参数的完整性（初始化时调用）
     * 检查：1. 特征列非空 2. 数值参数数量与数值列匹配（警告级）
     * @throws ModelException 关键配置缺失时抛出（终止初始化）
     */
    private void validateConfigParams() {
        // 校验数值特征列非空
        if (numCols == null || numCols.isEmpty()) {
            throw new ModelException("CTR v1: Numeric columns in config are null or empty", "EMPTY_NUM_COLS");
        }

        // 校验分类特征列非空
        if (catCols == null || catCols.isEmpty()) {
            throw new ModelException("CTR v1: Categorical columns in config are null or empty", "EMPTY_CAT_COLS");
        }

        // 校验数值参数数量匹配（警告级，允许部分缺失但记录日志）
        int numericParamCount = preprocessorParam.getNumericParams() != null
                ? preprocessorParam.getNumericParams().size()
                : 0;
        if (numericParamCount != numCols.size()) {
            log.warn("CTR v1: Numeric param count mismatch (config has: {}, required: {})",
                    numericParamCount, numCols.size());
        }

        // 校验分类参数数量匹配（警告级）
        int categoricalParamCount = preprocessorParam.getCategoricalParams() != null
                ? preprocessorParam.getCategoricalParams().size()
                : 0;
        if (categoricalParamCount != catCols.size()) {
            log.warn("CTR v1: Categorical param count mismatch (config has: {}, required: {})",
                    categoricalParamCount, catCols.size());
        }
    }
}