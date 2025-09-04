package com.example.demo.service;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.analysis.function.Log1p;
import org.python.core.PyDictionary;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.*;

/**
 * @Description 模型预测工程的特征预处理服务类
 * @Author charles
 * @Date 2025/9/3 23:04
 * @Version 1.0.0
 */
@Service
@Slf4j
public class PreprocessorService {
    private static final String[] NUM_COLS = {"I1", "I2", "I3", "I4", "I5", "I6", "I7", "I8", "I9", "I10", "I11", "I12", "I13"};
    private static final String[] CAT_COLS = {"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C21", "C22", "C23", "C24", "C25", "C26"};
    private static final String UNK_MARKER = "UNK";
    private static final double LOG1P_LOWER_BOUND = -0.999;

    @Value("${model.onnx.preprocessor-path}")
    private Resource preprocessorResource;

    private Map<String, Double> trainMedianValues;
    private Map<String, Set<String>> trainedCategories;
    private Map<String, Map<String, Integer>> labelEncoders;
    // 将scalerParams改为存储均值和标准差的Map
    private Map<String, Double> scalerMean;
    private Map<String, Double> scalerScale;

    @PostConstruct
    public void initPreprocessor() {
        log.info("Starting to load preprocessor parameter file: {}", preprocessorResource.getFilename());
        try {
            File preprocessorFile = preprocessorResource.getFile();
            if (!preprocessorFile.exists()) {
                throw new RuntimeException("Preprocessor file does not exist: " + preprocessorFile.getAbsolutePath());
            }

            try (PythonInterpreter pyInterp = new PythonInterpreter()) {
                pyInterp.exec("import pickle");
                pyInterp.exec(String.format(
                        "with open('%s', 'rb') as f: preprocessor = pickle.load(f)",
                        preprocessorFile.getAbsolutePath().replace("\\", "\\\\")
                ));

                this.trainMedianValues = loadTrainMedianValues(pyInterp);
                this.trainedCategories = loadTrainedCategories(pyInterp);
                this.labelEncoders = loadLabelEncoders(pyInterp);
                // 加载scaler参数（均值和标准差）
                loadScalerParams(pyInterp);
            }

            validateLoadedParams();
            log.info("Preprocessor parameters loaded successfully");

        } catch (Exception e) {
            log.error("Failed to load preprocessor parameters", e);
            throw new RuntimeException("Preprocessor init failed", e);
        }
    }

    public float[][] preprocessBatch(List<Map<String, String>> rawSamples) {
        log.info("Starting batch preprocessing for {} raw samples", rawSamples.size());
        float[][] batchFeatures = new float[rawSamples.size()][NUM_COLS.length + CAT_COLS.length];
        for (int i = 0; i < rawSamples.size(); i++) {
            batchFeatures[i] = preprocessSingleSample(rawSamples.get(i));
        }
        log.info("Batch preprocessing completed, generated {} feature vectors", batchFeatures.length);
        return batchFeatures;
    }

    public float[] preprocessSingleSample(Map<String, String> rawSample) {
        validateRawSample(rawSample);
        float[] processedFeatures = new float[NUM_COLS.length + CAT_COLS.length];
        int featureIndex = 0;
        Log1p log1p = new Log1p();

        // 处理数值特征
        for (String numCol : NUM_COLS) {
            String rawVal = rawSample.getOrDefault(numCol, "");
            double numericVal = handleNumericMissingValue(rawVal, numCol);
            double log1pVal = log1p.value(Math.max(numericVal, LOG1P_LOWER_BOUND));

            // 使用新的scaler参数进行标准化
            double mean = scalerMean.getOrDefault(numCol, 0.0);
            double scale = scalerScale.getOrDefault(numCol, 1.0);
            double standardizedVal = (log1pVal - mean) / scale;

            processedFeatures[featureIndex++] = (float) standardizedVal;
        }

        // 处理类别特征
        for (String catCol : CAT_COLS) {
            String rawVal = rawSample.getOrDefault(catCol, "");
            String processedCat = rawVal.isEmpty() ? UNK_MARKER : rawVal;

            Set<String> highFreqCats = trainedCategories.get(catCol);
            if (highFreqCats == null || !highFreqCats.contains(processedCat)) {
                processedCat = UNK_MARKER;
                log.debug("Categorical feature {} value '{}' not in trained set, replaced with UNK", catCol, rawVal);
            }

            Map<String, Integer> encoder = labelEncoders.get(catCol);
            int encodedVal = encoder.getOrDefault(processedCat, -1);
            processedFeatures[featureIndex++] = (float) encodedVal;
        }

        return processedFeatures;
    }

    private Map<String, Double> loadTrainMedianValues(PythonInterpreter pyInterp) {
        Map<String, Double> medianMap = new HashMap<>();
        PyObject pyMedianDict = pyInterp.eval("preprocessor['train_median_values']");
        PyDictionary pyDict = (PyDictionary) pyMedianDict;

        for (Object key : pyDict.keys()) {
            String featureName = key.toString();
            if (isValidNumericFeature(featureName)) {
                double median = Double.parseDouble(pyDict.get(key).toString());
                medianMap.put(featureName, median);
            }
        }
        log.info("Loaded median values for {} numeric features", medianMap.size());
        return medianMap;
    }

    private Map<String, Set<String>> loadTrainedCategories(PythonInterpreter pyInterp) {
        Map<String, Set<String>> categoryMap = new HashMap<>();
        PyObject pyCategoryDict = pyInterp.eval("preprocessor['trained_categories']");
        PyDictionary pyDict = (PyDictionary) pyCategoryDict;

        for (Object key : pyDict.keys()) {
            String featureName = key.toString();
            if (isValidCategoricalFeature(featureName)) {
                PyList pyList = (PyList) pyDict.get(key);
                Set<String> highFreqCats = new HashSet<>();
                for (Object pyCat : pyList) {
                    highFreqCats.add(pyCat.toString());
                }
                categoryMap.put(featureName, highFreqCats);
            }
        }
        log.info("Loaded trained categories for {} categorical features", categoryMap.size());
        return categoryMap;
    }

    private Map<String, Map<String, Integer>> loadLabelEncoders(PythonInterpreter pyInterp) {
        Map<String, Map<String, Integer>> encoderMap = new HashMap<>();
        PyObject pyEncoderDict = pyInterp.eval("preprocessor['label_encoders']");
        PyDictionary pyDict = (PyDictionary) pyEncoderDict;

        for (Object key : pyDict.keys()) {
            String featureName = key.toString();
            if (isValidCategoricalFeature(featureName)) {
                // 直接获取类别→编码的映射字典
                PyDictionary encoderDict = (PyDictionary) pyDict.get(key);
                Map<String, Integer> catToIntMap = new HashMap<>();

                // 遍历字典填充映射关系
                for (Object catKey : encoderDict.keys()) {
                    String category = catKey.toString();
                    int code = Integer.parseInt(encoderDict.get(catKey).toString());
                    catToIntMap.put(category, code);
                }

                encoderMap.put(featureName, catToIntMap);
                log.info("Loaded label encoder for categorical feature {}, containing {} categories", featureName, catToIntMap.size());
            }
        }
        return encoderMap;
    }

    private void loadScalerParams(PythonInterpreter pyInterp) {
        scalerMean = new HashMap<>();
        scalerScale = new HashMap<>();

        // 从preprocessor中获取scaler_mean和scaler_scale字典
        PyDictionary pyMeanDict = (PyDictionary) pyInterp.eval("preprocessor['scaler_mean']");
        PyDictionary pyScaleDict = (PyDictionary) pyInterp.eval("preprocessor['scaler_scale']");

        // 加载均值参数
        for (Object key : pyMeanDict.keys()) {
            String featureName = key.toString();
            if (isValidNumericFeature(featureName)) {
                double mean = Double.parseDouble(pyMeanDict.get(key).toString());
                scalerMean.put(featureName, mean);
            }
        }

        // 加载标准差参数
        for (Object key : pyScaleDict.keys()) {
            String featureName = key.toString();
            if (isValidNumericFeature(featureName)) {
                double scale = Double.parseDouble(pyScaleDict.get(key).toString());
                // 避免除零错误
                if (scale < 1e-9) {
                    scale = 1e-9;
                    log.warn("Standard deviation for numeric feature {} is too small, adjusted to 1e-9 to avoid division by zero", featureName);
                }
                scalerScale.put(featureName, scale);
            }
        }

        log.info("Loaded normalization parameters for numeric features - means: {}, standard deviations: {}",
                scalerMean.size(), scalerScale.size());
    }

    private double handleNumericMissingValue(String rawVal, String featureName) {
        if (rawVal == null || rawVal.trim().isEmpty()) {
            double median = trainMedianValues.getOrDefault(featureName, 0.0);
            log.debug("Missing value for numeric feature {}, replaced with median: {}", featureName, median);
            return median;
        }
        try {
            return Double.parseDouble(rawVal.trim());
        } catch (NumberFormatException e) {
            double median = trainMedianValues.getOrDefault(featureName, 0.0);
            log.warn("Invalid numeric value '{}' for feature {}, replaced with median: {}", rawVal, featureName, median, e);
            return median;
        }
    }

    private void validateRawSample(Map<String, String> rawSample) {
        for (String col : NUM_COLS) {
            if (!rawSample.containsKey(col)) {
                throw new IllegalArgumentException("Missing numeric feature: " + col);
            }
        }
        for (String col : CAT_COLS) {
            if (!rawSample.containsKey(col)) {
                throw new IllegalArgumentException("Missing categorical feature: " + col);
            }
        }
        log.debug("Raw sample validation passed - all required features are present");
    }

    private void validateLoadedParams() {
        if (trainMedianValues.size() != NUM_COLS.length) {
            throw new RuntimeException("Mismatch in numeric feature median count - expected: " + NUM_COLS.length + ", actual: " + trainMedianValues.size());
        }
        if (trainedCategories.size() != CAT_COLS.length) {
            throw new RuntimeException("Mismatch in categorical feature high-frequency category count - expected: " + CAT_COLS.length + ", actual: " + trainedCategories.size());
        }
        if (scalerMean.size() != NUM_COLS.length) {
            throw new RuntimeException("Mismatch in numeric feature mean count - expected: " + NUM_COLS.length + ", actual: " + scalerMean.size());
        }
        if (scalerScale.size() != NUM_COLS.length) {
            throw new RuntimeException("Mismatch in numeric feature standard deviation count - expected: " + NUM_COLS.length + ", actual: " + scalerScale.size());
        }
        log.debug("All loaded preprocessor parameters passed validation");
    }

    private boolean isValidNumericFeature(String featureName) {
        for (String col : NUM_COLS) {
            if (col.equals(featureName)) return true;
        }
        log.debug("Feature '{}' is not a valid numeric feature, skipped", featureName);
        return false;
    }

    private boolean isValidCategoricalFeature(String featureName) {
        for (String col : CAT_COLS) {
            if (col.equals(featureName)) return true;
        }
        log.debug("Feature '{}' is not a valid categorical feature, skipped", featureName);
        return false;
    }
}