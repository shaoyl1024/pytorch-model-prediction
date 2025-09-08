package com.example.demo.preprocessor.impl;

import com.example.demo.domain.PreprocessorParam;
import com.example.demo.exception.ModelException;
import com.example.demo.preprocessor.AbstractPreprocessor;
import lombok.extern.slf4j.Slf4j;
import org.python.core.PyDictionary;
import org.python.core.PyList;
import org.python.util.PythonInterpreter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.*;

/**
 * @Description 基于Pickle文件的CTR v1预处理器
 * @Author charles
 * @Date 2025/9/6 01:13
 * @Version 1.0.0
 */
@Service("ctrV1Preprocessor")
@Slf4j
public class CTRV1Preprocessor extends AbstractPreprocessor {

    // ============================================================================
    // 私有参数：从Pickle文件加载的预处理配置（初始化后不可变）
    // ============================================================================
    /** 数值特征中位数（用于缺失值填充） */
    private Map<String, Double> trainMedianValues;
    /** 分类特征高频值集合（低频值转为UNK） */
    private Map<String, Set<String>> trainedCategories;
    /** 分类特征标签编码映射（字符串→整数，模型输入需数值型） */
    private Map<String, Map<String, Integer>> labelEncoders;
    /** 数值特征均值（用于标准化计算：(x-mean)/scale） */
    private Map<String, Double> scalerMean;
    /** 数值特征标准差（用于标准化，避免除零） */
    private Map<String, Double> scalerScale;

    // ============================================================================
    // 静态配置：CTR v1模型固定的特征列定义（不可修改，避免意外篡改）
    // ============================================================================
    /** 数值特征列名（与原始数据字段名、Pickle参数键完全一致） */
    private static final List<String> NUM_COLS = Collections.unmodifiableList(Arrays.asList(
            "I1", "I2", "I3", "I4", "I5", "I6", "I7", "I8", "I9", "I10", "I11", "I12", "I13"
    ));
    /** 分类特征列名（与原始数据字段名、Pickle参数键完全一致） */
    private static final List<String> CAT_COLS = Collections.unmodifiableList(Arrays.asList(
            "C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10",
            "C11", "C12", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20",
            "C21", "C22", "C23", "C24", "C25", "C26"
    ));

    // ============================================================================
    // 外部配置：从配置文件注入的Pickle文件路径
    // ============================================================================
    /** CTR v1预处理器Pickle文件路径（配置项：model.ctr_v1.preprocessor-path） */
    @Value("${model.ctr_v1.preprocessor-path}")
    private Resource preprocessorResource;


    // ============================================================================
    // 抽象方法实现：父类定义的差异化逻辑（核心预处理流程定制）
    // ============================================================================
    /**
     * 初始化预处理参数（Spring生命周期方法）
     * 流程：1. 校验Pickle文件存在性 2. 启动Python解释器加载文件 3. 解析参数到Java变量 4. 校验参数完整性
     * @throws ModelException Pickle加载/解析失败时抛出（终止预处理流程）
     */
    @Override
    public void initParams() {
        log.info("Start initializing CTR v1 preprocessor from Pickle file");
        try {
            // 1. 转换Resource为File，校验文件存在性
            File pickleFile = preprocessorResource.getFile();
            if (!pickleFile.exists()) {
                throw new ModelException(
                        "CTR v1 Pickle file not found: " + pickleFile.getAbsolutePath(),
                        "PICKLE_FILE_NOT_FOUND"
                );
            }
            log.debug("CTR v1 Pickle file path: {}", pickleFile.getAbsolutePath());

            // 2. 启动Python解释器加载Pickle（try-with-resources自动关闭解释器）
            try (PythonInterpreter pyInterp = new PythonInterpreter()) {
                pyInterp.exec("import pickle");
                // 处理Windows路径转义（将\转为\\，Python识别路径）
                String escapedPath = pickleFile.getAbsolutePath().replace("\\", "\\\\");
                pyInterp.exec(String.format("with open('%s', 'rb') as f: preprocessor = pickle.load(f)", escapedPath));

                // 3. 解析Pickle中的核心参数（按类型分步骤加载）
                this.trainMedianValues = loadMediansFromPickle(pyInterp);
                this.trainedCategories = loadHighFreqCatsFromPickle(pyInterp);
                this.labelEncoders = loadLabelEncodersFromPickle(pyInterp);
                this.scalerMean = loadScalerMeanFromPickle(pyInterp);
                this.scalerScale = loadScalerScaleFromPickle(pyInterp);
            }

            // 4. 校验参数完整性（确保所有特征列都有对应参数）
            validatePickleParams();
            log.info("CTR v1 preprocessor initialized successfully | Numeric cols: {}, Categorical cols: {}",
                    NUM_COLS.size(), CAT_COLS.size());

        } catch (Exception e) {
            log.error("CTR v1 preprocessor initialization failed", e);
            throw new ModelException("CTR v1 Pickle preprocessor init failed", e);
        }
    }

    /**
     * 获取CTR v1的数值特征列名（实现父类抽象方法）
     * @return 不可变的数值特征列列表
     */
    @Override
    protected List<String> getNumericColumns() {
        return NUM_COLS;
    }

    /**
     * 获取CTR v1的分类特征列名（实现父类抽象方法）
     * @return 不可变的分类特征列列表
     */
    @Override
    protected List<String> getCategoricalColumns() {
        return CAT_COLS;
    }

    /**
     * 根据数值特征列名获取预处理参数DTO（实现父类抽象方法）
     * 从Pickle加载的参数中提取均值、中位数、标准差，封装为统一DTO
     * @param numericCol 数值特征列名（如"I1"）
     * @return 数值特征预处理参数DTO（无匹配列时返回默认参数，避免空指针）
     */
    @Override
    protected PreprocessorParam.NumericParam getNumericParam(String numericCol) {
        // 校验列名合法性
        if (!NUM_COLS.contains(numericCol)) {
            log.warn("CTR v1: Invalid numeric column '{}' (not in predefined list)", numericCol);
            return new PreprocessorParam.NumericParam(0.0, 0.0, 1.0); // 默认参数：无标准化效果
        }

        // 从Pickle加载的参数中取值（无值时用默认值）
        double mean = scalerMean.getOrDefault(numericCol, 0.0);
        double median = trainMedianValues.getOrDefault(numericCol, 0.0);
        double scale = scalerScale.getOrDefault(numericCol, 1.0);

        // 注意：CTRV1Param.NumericParam构造器顺序为（mean, median, scale）
        return new PreprocessorParam.NumericParam(mean, median, scale);
    }

    /**
     * 根据分类特征列名获取预处理参数DTO（实现父类抽象方法）
     * 从Pickle加载的参数中提取高频值集合、编码映射，封装为统一DTO
     * @param categoricalCol 分类特征列名（如"C1"）
     * @return 分类特征预处理参数DTO（无匹配列时返回空集合，避免空指针）
     */
    @Override
    protected PreprocessorParam.CategoricalParam getCategoricalParam(String categoricalCol) {
        // 校验列名合法性
        if (!CAT_COLS.contains(categoricalCol)) {
            log.warn("CTR v1: Invalid categorical column '{}' (not in predefined list)", categoricalCol);
            return new PreprocessorParam.CategoricalParam(Collections.emptySet(), Collections.emptyMap(), -1);
        }

        // 从Pickle加载的参数中取值（无值时用空集合/默认编码）
        Set<String> highFreqSet = trainedCategories.getOrDefault(categoricalCol, Collections.emptySet());
        Map<String, Integer> codeMap = labelEncoders.getOrDefault(categoricalCol, Collections.emptyMap());
        int defaultCode = -1; // 未知值默认编码（与模型训练时保持一致）

        return new PreprocessorParam.CategoricalParam(highFreqSet, codeMap, defaultCode);
    }

    /**
     * 验证单条原始样本的合法性（实现父类抽象方法）
     * 校验样本是否包含所有必需的数值/分类特征，缺失则抛出异常
     * @param rawSample 原始样本（Map键为特征名，值为原始字符串）
     * @throws ModelException 缺失特征时抛出（终止当前样本预处理）
     */
    @Override
    protected void validateSample(Map<String, String> rawSample) {
        // 校验数值特征缺失
        for (String numCol : NUM_COLS) {
            if (!rawSample.containsKey(numCol)) {
                throw new ModelException(
                        "CTR v1: Missing required numeric feature '" + numCol + "'",
                        "MISSING_NUMERIC_FEATURE"
                );
            }
        }

        // 校验分类特征缺失
        for (String catCol : CAT_COLS) {
            if (!rawSample.containsKey(catCol)) {
                throw new ModelException(
                        "CTR v1: Missing required categorical feature '" + catCol + "'",
                        "MISSING_CATEGORICAL_FEATURE"
                );
            }
        }
    }

    /**
     * 数值特征预处理（实现父类抽象方法）
     * 流程：1. 缺失值填充（用中位数） 2. Log1p转换（处理长尾分布） 3. 标准化（(x-mean)/scale）
     * @param rawVal 原始数值特征值（可能为空/非法字符串）
     * @param numericCol 数值特征列名（用于获取对应预处理参数）
     * @return 处理后的浮点型特征值（适配模型输入）
     */
    @Override
    protected float processNumericFeature(String rawVal, String numericCol) {
        PreprocessorParam.NumericParam paramDTO = getNumericParam(numericCol);
        if (paramDTO == null) {
            log.error("CTR v1: Numeric param DTO is null for column '{}'", numericCol);
            return 0.0f;
        }

        // 1. 处理缺失值/非法值：空值→中位数，无法解析→中位数
        double numericVal;
        try {
            numericVal = (rawVal == null || rawVal.trim().isEmpty())
                    ? paramDTO.getMedian() // 缺失值用中位数填充（符合CTR v1训练逻辑）
                    : Double.parseDouble(rawVal.trim());
        } catch (NumberFormatException e) {
            log.warn("CTR v1: Invalid numeric value '{}' for column '{}', use median: {}",
                    rawVal, numericCol, paramDTO.getMedian());
            numericVal = paramDTO.getMedian();
        }

        // 2. Log1p转换：处理数值长尾分布，避免极端值影响（要求输入>LOG1P_LOWER_BOUND=-0.999）
        double log1pVal = Math.log1p(Math.max(numericVal, LOG1P_LOWER_BOUND));

        // 3. 标准化：消除量级差异，公式：(log1p值 - 均值) / 安全标准差（避免除零）
        double safeScale = Math.max(paramDTO.getScale(), MIN_SCALE);
        double standardizedVal = (log1pVal - paramDTO.getMean()) / safeScale;

        return (float) standardizedVal;
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
        PreprocessorParam.CategoricalParam paramDTO = getCategoricalParam(categoricalCol);
        if (paramDTO == null) {
            log.error("CTR v1: Categorical param DTO is null for column '{}'", categoricalCol);
            return -1; // 返回默认未知编码
        }

        // 1. 空值/空白值→UNK标记（统一未知值表示）
        String processedVal = (rawVal == null || rawVal.trim().isEmpty())
                ? UNK_MARKER
                : rawVal.trim();

        // 2. 低频值过滤：不在高频集合中的值→UNK（减少稀疏性，符合训练逻辑）
        Set<String> highFreqSet = paramDTO.getHighFreqValues();
        if (!highFreqSet.isEmpty() && !highFreqSet.contains(processedVal)) {
            log.debug("CTR v1: Low-frequency value '{}' for column '{}' replaced with UNK",
                    processedVal, categoricalCol);
            processedVal = UNK_MARKER;
        }

        // 3. 标签编码：根据Pickle中的映射表转换，无匹配→默认编码
        Map<String, Integer> codeMap = paramDTO.getCodeMap();
        Integer code = codeMap.getOrDefault(processedVal, paramDTO.getDefaultCode());
        if (code == paramDTO.getDefaultCode()) {
            log.debug("CTR v1: Value '{}' for column '{}' not in codeMap, use default code: {}",
                    processedVal, categoricalCol, paramDTO.getDefaultCode());
        }

        return code;
    }


    // ============================================================================
    // 私有工具方法：Pickle参数解析（仅内部调用，按参数类型拆分，降低耦合）
    // ============================================================================
    /**
     * 从Pickle加载数值特征中位数（train_median_values）
     * @param pyInterp 已加载Pickle对象的Python解释器
     * @return 中位数映射：key=数值列名，value=对应中位数
     */
    private Map<String, Double> loadMediansFromPickle(PythonInterpreter pyInterp) {
        Map<String, Double> medianMap = new HashMap<>(NUM_COLS.size());
        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['train_median_values']");

        for (Object key : pyDict.keys()) {
            String colName = key.toString();
            // 仅保留预定义的数值列，过滤无关参数
            if (NUM_COLS.contains(colName)) {
                String medianStr = pyDict.get(key).toString();
                medianMap.put(colName, Double.parseDouble(medianStr));
            }
        }

        log.debug("CTR v1: Loaded median values for {} numeric columns", medianMap.size());
        return medianMap;
    }

    /**
     * 从Pickle加载分类特征高频值集合（trained_categories）
     * @param pyInterp 已加载Pickle对象的Python解释器
     * @return 高频值映射：key=分类列名，value=对应高频值集合
     */
    private Map<String, Set<String>> loadHighFreqCatsFromPickle(PythonInterpreter pyInterp) {
        Map<String, Set<String>> highFreqMap = new HashMap<>(CAT_COLS.size());
        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['trained_categories']");

        for (Object key : pyDict.keys()) {
            String colName = key.toString();
            // 仅保留预定义的分类列，过滤无关参数
            if (CAT_COLS.contains(colName)) {
                PyList pyHighFreqList = (PyList) pyDict.get(key);
                Set<String> highFreqSet = new HashSet<>(pyHighFreqList.size());

                // 转换Python列表为Java集合
                for (Object val : pyHighFreqList) {
                    highFreqSet.add(val.toString());
                }

                highFreqMap.put(colName, highFreqSet);
            }
        }

        log.debug("CTR v1: Loaded high-frequency categories for {} categorical columns", highFreqMap.size());
        return highFreqMap;
    }

    /**
     * 从Pickle加载分类特征标签编码映射（label_encoders）
     * @param pyInterp 已加载Pickle对象的Python解释器
     * @return 编码映射：key=分类列名，value=该列{分类值→整数编码}的映射
     */
    private Map<String, Map<String, Integer>> loadLabelEncodersFromPickle(PythonInterpreter pyInterp) {
        Map<String, Map<String, Integer>> encoderMap = new HashMap<>(CAT_COLS.size());
        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['label_encoders']");

        for (Object key : pyDict.keys()) {
            String colName = key.toString();
            // 仅保留预定义的分类列，过滤无关参数
            if (CAT_COLS.contains(colName)) {
                PyDictionary pyColEncoder = (PyDictionary) pyDict.get(key);
                Map<String, Integer> colEncoderMap = new HashMap<>(pyColEncoder.size());

                // 转换Python字典为Java映射（分类值→整数编码）
                for (Object catKey : pyColEncoder.keys()) {
                    String categoryVal = catKey.toString();
                    String codeStr = pyColEncoder.get(catKey).toString();
                    colEncoderMap.put(categoryVal, Integer.parseInt(codeStr));
                }

                encoderMap.put(colName, colEncoderMap);
            }
        }

        log.debug("CTR v1: Loaded label encoders for {} categorical columns", encoderMap.size());
        return encoderMap;
    }

    /**
     * 从Pickle加载数值特征均值（scaler_mean）
     * @param pyInterp 已加载Pickle对象的Python解释器
     * @return 均值映射：key=数值列名，value=对应均值
     */
    private Map<String, Double> loadScalerMeanFromPickle(PythonInterpreter pyInterp) {
        Map<String, Double> meanMap = new HashMap<>(NUM_COLS.size());
        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['scaler_mean']");

        for (Object key : pyDict.keys()) {
            String colName = key.toString();
            // 仅保留预定义的数值列，过滤无关参数
            if (NUM_COLS.contains(colName)) {
                String meanStr = pyDict.get(key).toString();
                meanMap.put(colName, Double.parseDouble(meanStr));
            }
        }

        log.debug("CTR v1: Loaded scaler mean for {} numeric columns", meanMap.size());
        return meanMap;
    }

    /**
     * 从Pickle加载数值特征标准差（scaler_scale），并处理过小值（避免除零）
     * @param pyInterp 已加载Pickle对象的Python解释器
     * @return 安全标准差映射：key=数值列名，value=≥MIN_SCALE的标准差
     */
    private Map<String, Double> loadScalerScaleFromPickle(PythonInterpreter pyInterp) {
        Map<String, Double> scaleMap = new HashMap<>(NUM_COLS.size());
        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['scaler_scale']");

        for (Object key : pyDict.keys()) {
            String colName = key.toString();
            // 仅保留预定义的数值列，过滤无关参数
            if (NUM_COLS.contains(colName)) {
                String scaleStr = pyDict.get(key).toString();
                double scale = Double.parseDouble(scaleStr);
                // 处理过小值：确保标准差≥MIN_SCALE，避免标准化时除零
                double safeScale = Math.max(scale, MIN_SCALE);
                scaleMap.put(colName, safeScale);
            }
        }

        log.debug("CTR v1: Loaded safe scaler scale for {} numeric columns", scaleMap.size());
        return scaleMap;
    }

    /**
     * 校验Pickle加载的参数完整性（确保所有预定义列都有对应参数）
     * @throws ModelException 参数缺失时抛出（终止初始化）
     */
    private void validatePickleParams() {
        // 校验数值特征参数完整性（中位数、均值、标准差需覆盖所有数值列）
        if (trainMedianValues.size() != NUM_COLS.size()) {
            throw new ModelException(
                    "CTR v1: Numeric median count mismatch (loaded: " + trainMedianValues.size()
                            + ", required: " + NUM_COLS.size() + ")",
                    "NUMERIC_PARAM_MISMATCH"
            );
        }
        if (scalerMean.size() != NUM_COLS.size()) {
            throw new ModelException(
                    "CTR v1: Numeric mean count mismatch (loaded: " + scalerMean.size()
                            + ", required: " + NUM_COLS.size() + ")",
                    "NUMERIC_PARAM_MISMATCH"
            );
        }

        // 校验分类特征参数完整性（高频值、编码映射需覆盖所有分类列）
        if (trainedCategories.size() != CAT_COLS.size()) {
            throw new ModelException(
                    "CTR v1: Categorical high-freq count mismatch (loaded: " + trainedCategories.size()
                            + ", required: " + CAT_COLS.size() + ")",
                    "CATEGORICAL_PARAM_MISMATCH"
            );
        }
        if (labelEncoders.size() != CAT_COLS.size()) {
            throw new ModelException(
                    "CTR v1: Categorical encoder count mismatch (loaded: " + labelEncoders.size()
                            + ", required: " + CAT_COLS.size() + ")",
                    "CATEGORICAL_PARAM_MISMATCH"
            );
        }
    }
}