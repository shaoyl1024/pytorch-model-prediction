package com.example.demo.service;

import com.example.demo.exception.ModelException;
import com.example.demo.model.PredictionResult;
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
public class DataLoaderService {
    @Value("${model.test.data-path}")
    private Resource testDataResource;

    @Value("${model.test.separator}")
    private String separator;

    // 限制最大加载数量为10条
    private static final int MAX_RECORDS = 20;

    /**
     * 测试数据表头 - 采用方式1
     * 方式1：硬编码
     * 方式2：从criteo_preprocessor.json文件映射到PreprocessorParam对象中解析
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

            log.info("数据加载完成 | 实际加载: {} 条 (最大限制: {})", loadedCount, MAX_RECORDS);
            return rawSamples;

        } catch (IOException e) {
            log.error("加载测试数据时发生IO错误", e);
            throw new ModelException("数据加载失败", e);
        }
    }

    private List<String> getAndValidatePredefinedFields() {
        List<String> numericFields = cleanAndValidateFieldList(
                List.of(NUM_COLS), "数值型字段(num_cols)");

        List<String> categoricalFields = cleanAndValidateFieldList(
                List.of(CAT_COLS), "分类型字段(cat_cols)");

        List<String> allFields = new ArrayList<>(numericFields);
        List<String> duplicateFields = categoricalFields.stream()
                .filter(allFields::contains)
                .collect(Collectors.toList());

        if (!duplicateFields.isEmpty()) {
            throw new ModelException(
                    "预定义字段中存在重复: " + duplicateFields,
                    "DUPLICATE_PREDEFINED_FIELDS");
        }

        allFields.addAll(categoricalFields);
        log.info("预定义字段加载完成 | 数值型: {}, 分类型: {}, 总计: {}",
                numericFields.size(), categoricalFields.size(), allFields.size());

        return allFields;
    }

    private List<String> cleanAndValidateFieldList(List<String> rawFields, String fieldType) {
        if (rawFields == null) {
            throw new ModelException(fieldType + "配置为null", "NULL_FIELD_CONFIG");
        }

        List<String> cleanedFields = rawFields.stream()
                .collect(Collectors.toList());

        if (cleanedFields.isEmpty()) {
            log.warn("{}清洗后为空", fieldType);
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