package com.example.demo.data;

import com.example.demo.exception.ModelException;
import com.example.demo.domain.PredictionResult;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description 测试数据加载服务类，负责加载和解析模型预测所需的测试数据
 * @Author charles
 * @Date 2025/9/3 23:04
 * @Version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestDataLoaderService {
    @Value("${model.test.data-path}")
    private Resource testDataResource;

    @Value("${model.test.separator}")
    private String separator;

    private static final int MAX_RECORDS = 20;

    /**
     * 测试数据表头 - 采用方式1
     * <p>方式1：硬编码</p>
     * <p>方式2：从criteo_preprocessor.json文件映射到PreprocessorParam对象中解析</p>
     */
    private static final String[] NUM_COLS = {"I1", "I2", "I3", "I4", "I5", "I6", "I7", "I8", "I9", "I10", "I11", "I12", "I13"};
    private static final String[] CAT_COLS = {"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C21", "C22", "C23", "C24", "C25", "C26"};

    /**
     * 加载测试数据（最多加载10条，使用预定义字段配置）
     *
     * @return
     * @throws IOException
     * @throws CsvValidationException
     */
    public List<Map<String, String>> loadTestData() throws IOException, CsvValidationException {

        List<String> predefinedFields = getAndValidatePredefinedFields();
        int expectedFieldCount = predefinedFields.size();

        List<Map<String, String>> rawSamples = new ArrayList<>(MAX_RECORDS);
        int loadedCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(testDataResource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && loadedCount < MAX_RECORDS) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] fieldValues = line.split(separator, -1);

                // 映射字段并添加到结果集
                LinkedHashMap<String, String> record = new LinkedHashMap<>(expectedFieldCount + 1);
                for (int i = 0; i < expectedFieldCount; i++) {
                    record.put(predefinedFields.get(i), fieldValues[i]);
                }
                rawSamples.add(record);
                loadedCount++;
            }

            log.info("Test data loading completed | Actually loaded: {} records (Max limit: {})", loadedCount, MAX_RECORDS);
            return rawSamples;

        } catch (IOException e) {
            log.error("IO error occurred while loading test data", e);
            throw new ModelException("Test data loading failed", e);
        }
    }

    private List<String> getAndValidatePredefinedFields() {
        List<String> numericFields = cleanAndValidateFieldList(
                List.of(NUM_COLS), "numeric fields (num_cols)");

        List<String> categoricalFields = cleanAndValidateFieldList(
                List.of(CAT_COLS), "categorical fields (cat_cols)");

        List<String> allFields = new ArrayList<>(numericFields);
        List<String> duplicateFields = categoricalFields.stream()
                .filter(allFields::contains)
                .collect(Collectors.toList());

        if (!duplicateFields.isEmpty()) {
            throw new ModelException(
                    "Duplicate fields found in predefined fields: " + duplicateFields,
                    "DUPLICATE_PREDEFINED_FIELDS");
        }

        allFields.addAll(categoricalFields);
        log.info("Predefined fields loaded successfully | Numeric fields: {}, Categorical fields: {}, Total fields: {}",
                numericFields.size(), categoricalFields.size(), allFields.size());

        return allFields;
    }

    /**
     * 清洗并验证字段列表
     * 清洗逻辑：过滤空字段（若有）；验证逻辑：字段列表非null
     *
     * @param rawFields 原始字段列表
     * @param fieldType 字段类型描述（用于日志和异常信息）
     * @return 清洗后的非空字段列表
     */
    private List<String> cleanAndValidateFieldList(List<String> rawFields, String fieldType) {
        if (rawFields == null) {
            throw new ModelException("Configuration for " + fieldType + " is null", "NULL_FIELD_CONFIG");
        }

        // 过滤空字符串字段（若存在配置错误导致的空字段）
        List<String> cleanedFields = rawFields.stream()
                .filter(field -> field != null && !field.trim().isEmpty())
                .collect(Collectors.toList());

        if (cleanedFields.isEmpty()) {
            log.warn("{} is empty after cleaning", fieldType);
        }

        return cleanedFields;
    }

    public List<PredictionResult> wrapPredictionResult(List<Map<String, String>> rawSamples, float[] ctrProbs) {
        List<PredictionResult> results = new ArrayList<>(rawSamples.size());
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < rawSamples.size(); i++) {
            Map<String, String> rawSample = rawSamples.get(i);
            String sampleId = rawSample.getOrDefault("sampleId", String.valueOf(i));
            String rawFeatures = rawSample.toString();
            double ctrProb = ctrProbs[i];

            results.add(new PredictionResult(sampleId, rawFeatures, ctrProb, now));
        }
        return results;
    }
}