package com.example.demo.preprocessor;

import com.example.demo.model.PreprocessorParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.analysis.function.Log1p;
import org.python.core.PyDictionary;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.*;

/**
 * 基于Pickle文件的预处理器（子类1）：实现从Pickle加载参数
 */
@Service("pickleBasedPreprocessor")
@Slf4j
public class PickleBasedPreprocessor extends AbstractPreprocessorService {
    // -------------------------- 子类私有参数（从Pickle加载） --------------------------
    private Map<String, Double> trainMedianValues; // 数值特征中位数
    private Map<String, Set<String>> trainedCategories; // 类别特征高频值
    private Map<String, Map<String, Integer>> labelEncoders; // 类别编码映射
    private Map<String, Double> scalerMean; // 数值特征均值（标准化用）
    private Map<String, Double> scalerScale; // 数值特征标准差（标准化用）

    // -------------------------- 子类特有配置 --------------------------
    // 数值/类别列（子类自定义）
    private static final List<String> NUM_COLS = Arrays.asList("I1", "I2", "I3", "I4", "I5", "I6", "I7", "I8", "I9", "I10", "I11", "I12", "I13");
    private static final List<String> CAT_COLS = Arrays.asList("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C21", "C22", "C23", "C24", "C25", "C26");

    // Pickle文件路径（从配置注入）
    @Value("${model.ctr_v1.preprocessor-path}")
    private Resource preprocessorResource;

    // -------------------------- 实现父类抽象方法 --------------------------
    @Override
    public void initParams() {
        log.info("Loading preprocessor from Pickle file: {}", preprocessorResource.getFilename());
        try {
            File pickleFile = preprocessorResource.getFile();
            if (!pickleFile.exists()) {
                throw new RuntimeException("Pickle file not found: " + pickleFile.getAbsolutePath());
            }

            // 加载Pickle文件（子类独有逻辑）
            try (PythonInterpreter pyInterp = new PythonInterpreter()) {
                pyInterp.exec("import pickle");
                pyInterp.exec(String.format(
                        "with open('%s', 'rb') as f: preprocessor = pickle.load(f)",
                        pickleFile.getAbsolutePath().replace("\\", "\\\\")
                ));

                // 解析Pickle中的参数（子类独有逻辑）
                this.trainMedianValues = loadMediansFromPickle(pyInterp);
                this.trainedCategories = loadHighFreqCatsFromPickle(pyInterp);
                this.labelEncoders = loadLabelEncodersFromPickle(pyInterp);
                this.scalerMean = loadScalerMeanFromPickle(pyInterp);
                this.scalerScale = loadScalerScaleFromPickle(pyInterp);
            }

            // 子类独有：验证参数完整性
            validatePickleParams();
            log.info("Pickle preprocessor initialized successfully");

        } catch (Exception e) {
            log.error("Failed to init Pickle preprocessor", e);
            throw new RuntimeException("Pickle preprocessor init failed", e);
        }
    }

    @Override
    protected List<String> getNumericColumns() {
        return NUM_COLS; // 子类返回自定义数值列
    }

    @Override
    protected List<String> getCategoricalColumns() {
        return CAT_COLS; // 子类返回自定义类别列
    }

    @Override
    protected PreprocessorParam.NumericParam getNumericParam(String numericCol) {
        // 子类实现：从Pickle加载的参数中提取DTO
        if (!NUM_COLS.contains(numericCol)) {
            log.warn("Invalid numeric column: {}", numericCol);
            return new PreprocessorParam.NumericParam(0.0, 0.0, 1.0);
        }

        double median = trainMedianValues.getOrDefault(numericCol, 0.0);
        double mean = scalerMean.getOrDefault(numericCol, 0.0);
        double scale = scalerScale.getOrDefault(numericCol, 1.0);
        return new PreprocessorParam.NumericParam(median, mean, scale);
    }

    @Override
    protected PreprocessorParam.CategoricalParam getCategoricalParam(String categoricalCol) {
        // 子类实现：从Pickle加载的参数中提取DTO
        if (!CAT_COLS.contains(categoricalCol)) {
            log.warn("Invalid categorical column: {}", categoricalCol);
            return new PreprocessorParam.CategoricalParam(Collections.emptySet(), Collections.emptyMap(), -1);
        }

        Set<String> highFreqSet = trainedCategories.getOrDefault(categoricalCol, Collections.emptySet());
        Map<String, Integer> codeMap = labelEncoders.getOrDefault(categoricalCol, Collections.emptyMap());
        int defaultCode = -1; // 子类自定义默认编码
        return new PreprocessorParam.CategoricalParam(highFreqSet, codeMap, defaultCode);
    }

    @Override
    protected void validateSample(Map<String, String> rawSample) {
        // 子类实现：严格验证特征存在性（原逻辑）
        for (String numCol : NUM_COLS) {
            if (!rawSample.containsKey(numCol)) {
                throw new IllegalArgumentException("Missing numeric feature: " + numCol);
            }
        }
        for (String catCol : CAT_COLS) {
            if (!rawSample.containsKey(catCol)) {
                throw new IllegalArgumentException("Missing categorical feature: " + catCol);
            }
        }
    }

    // -------------------------- 子类独有工具方法 --------------------------
    private Map<String, Double> loadMediansFromPickle(PythonInterpreter pyInterp) {
        Map<String, Double> medianMap = new HashMap<>();
        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['train_median_values']");
        for (Object key : pyDict.keys()) {
            String col = key.toString();
            if (NUM_COLS.contains(col)) {
                medianMap.put(col, Double.parseDouble(pyDict.get(key).toString()));
            }
        }
        return medianMap;
    }

    private Map<String, Set<String>> loadHighFreqCatsFromPickle(PythonInterpreter pyInterp) {
        Map<String, Set<String>> catMap = new HashMap<>();
        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['trained_categories']");
        for (Object key : pyDict.keys()) {
            String col = key.toString();
            if (CAT_COLS.contains(col)) {
                PyList pyList = (PyList) pyDict.get(key);
                Set<String> highFreq = new HashSet<>();
                for (Object val : pyList) highFreq.add(val.toString());
                catMap.put(col, highFreq);
            }
        }
        return catMap;
    }

    /**
     * 从Pickle加载类别特征的标签编码映射（label_encoders）
     * 风格对齐loadHighFreqCatsFromPickle：简洁流程、直接类型转换、仅过滤预定义列
     * @param pyInterp Python解释器（已加载preprocessor对象）
     * @return 编码映射：key=类别列名（CAT_COLS内），value=该列{类别值→编码}的映射
     */
    private Map<String, Map<String, Integer>> loadLabelEncodersFromPickle(PythonInterpreter pyInterp) {
        Map<String, Map<String, Integer>> encoderMap = new HashMap<>();

        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['label_encoders']");

        for (Object key : pyDict.keys()) {
            String col = key.toString();
            if (CAT_COLS.contains(col)) { // 只保留配置的类别列，过滤无关列
                PyDictionary pyColEncoder = (PyDictionary) pyDict.get(key); // 对应loadHighFreqCatsFromPickle的PyList
                Map<String, Integer> colEncoderMap = new HashMap<>();

                for (Object catKey : pyColEncoder.keys()) {
                    String categoryVal = catKey.toString(); // 类别值（如"male"/"female"）
                    String codeStr = pyColEncoder.get(catKey).toString(); // 编码值（如"1"/"2"）
                    colEncoderMap.put(categoryVal, Integer.parseInt(codeStr)); // 转为整数编码
                }

                encoderMap.put(col, colEncoderMap);
            }
        }

        return encoderMap;
    }

    private Map<String, Double> loadScalerMeanFromPickle(PythonInterpreter pyInterp) {
        // 1. 初始化Java端存储均值的Map（同loadHighFreqCatsFromPickle的catMap）
        Map<String, Double> meanMap = new HashMap<>();

        // 2. 获取Pickle中scaler_mean的顶层PyDictionary（同loadHighFreqCatsFromPickle的pyDict）
        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['scaler_mean']");

        // 3. 遍历所有列，仅处理预定义的数值列（NUM_COLS过滤，同CAT_COLS过滤逻辑）
        for (Object key : pyDict.keys()) {
            String col = key.toString();
            if (NUM_COLS.contains(col)) { // 只保留配置的数值列，过滤无关列
                // 4. 转换均值（Python数值→Java Double，直接强转字符串后解析，符合原风格）
                String meanStr = pyDict.get(key).toString();
                meanMap.put(col, Double.parseDouble(meanStr));
            }
        }

        return meanMap;
    }

    /**
     * 从Pickle加载数值特征的标准化标准差（scaler_scale）
     * 风格对齐loadHighFreqCatsFromPickle，额外处理标准差过小问题（业务必需）
     * @param pyInterp Python解释器（已加载preprocessor对象）
     * @return 标准差映射：key=数值列名（NUM_COLS内），value=安全标准差（≥MIN_SCALE）
     */
    private Map<String, Double> loadScalerScaleFromPickle(PythonInterpreter pyInterp) {
        // 1. 初始化Java端存储标准差的Map（同均值方法的meanMap）
        Map<String, Double> scaleMap = new HashMap<>();

        // 2. 获取Pickle中scaler_scale的顶层PyDictionary（同均值方法的pyDict）
        PyDictionary pyDict = (PyDictionary) pyInterp.eval("preprocessor['scaler_scale']");

        // 3. 遍历所有列，仅处理预定义的数值列（NUM_COLS过滤，与均值方法完全一致）
        for (Object key : pyDict.keys()) {
            String col = key.toString();
            if (NUM_COLS.contains(col)) { // 过滤非NUM_COLS列
                // 4. 转换标准差+处理过小值（业务核心：避免后续除零，不破坏简洁风格）
                String scaleStr = pyDict.get(key).toString();
                double scale = Double.parseDouble(scaleStr);
                // 用父类的MIN_SCALE确保标准差不小于安全值（与processNumericFeature逻辑一致）
                double safeScale = Math.max(scale, MIN_SCALE);

                // 5. 存入映射（安全标准差）
                scaleMap.put(col, safeScale);
            }
        }

        return scaleMap;
    }


    /**
     * 数值特征处理（公共逻辑：缺失值→Log1p→标准化）
     */
    @Override
    protected float processNumericFeature(String rawVal, String numericCol) {
        PreprocessorParam.NumericParam paramDTO = getNumericParam(numericCol);
        if (paramDTO == null) {
            log.error("Numeric param DTO is null for column: {}", numericCol);
            return 0.0f;
        }

        // 1. 处理缺失值/无效值（用中位数填充）
        double numericVal;
        try {
            numericVal = (rawVal == null || rawVal.trim().isEmpty())
                    ? paramDTO.getMean()
                    : Double.parseDouble(rawVal.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid numeric value '{}' for column {}, use median: {}", rawVal, numericCol, paramDTO.getMedian(), e);
            numericVal = paramDTO.getMedian();
        }

        // 2. Log1p转换（避免下界问题）
        double log1pVal = Math.log1p(Math.max(numericVal, LOG1P_LOWER_BOUND));

        // 3. 标准化（避免除零）
        double safeScale = Math.max(paramDTO.getScale(), MIN_SCALE);
        double standardizedVal = (log1pVal - paramDTO.getMedian()) / safeScale;

        return (float) standardizedVal;
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

    private void validatePickleParams() {
        if (trainMedianValues.size() != NUM_COLS.size()) {
            throw new RuntimeException("Numeric median count mismatch: " + trainMedianValues.size() + "/" + NUM_COLS.size());
        }
        if (trainedCategories.size() != CAT_COLS.size()) {
            throw new RuntimeException("Categorical high-freq count mismatch: " + trainedCategories.size() + "/" + CAT_COLS.size());
        }
    }
}