package com.example.demo.service;

import com.example.demo.exception.ModelException;
import com.example.demo.model.PreprocessorParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataLoaderService {

    @Value("${data.test-path}")
    private Resource testDataResource;

    @Value("${data.separator: }")
    private String separator;

    private final PreprocessorParam preprocessorParam;

    // 限制最大加载数量为10条
    private static final int MAX_RECORDS = 20;

    /**
     * 加载测试数据（最多加载10条，使用预定义字段配置）
     */
    public List<Map<String, String>> loadTestData() {
        List<String> predefinedFields = getAndValidatePredefinedFields();
        int expectedFieldCount = predefinedFields.size();

        List<Map<String, String>> data = new ArrayList<>(MAX_RECORDS);
        int loadedCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(testDataResource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int lineNumber = 0;

            // 读取数据行，直到达到最大数量或文件结束
            while ((line = reader.readLine()) != null && loadedCount < MAX_RECORDS) {
                lineNumber++;

                if (line.isEmpty()) {
                    log.warn("跳过空行 | 行号: {}", lineNumber);
                    continue;
                }

                String[] fieldValues = line.split(separator, -1);

                // 映射字段并添加到结果集
                LinkedHashMap<String, String> record = new LinkedHashMap<>(expectedFieldCount + 1);
                for (int i = 0; i < expectedFieldCount; i++) {
                    record.put(predefinedFields.get(i), fieldValues[i]);
                }
                data.add(record);
                loadedCount++; // 计数器递增
            }

            log.info("数据加载完成 | 实际加载: {} 条 (最大限制: {})", loadedCount, MAX_RECORDS);
            return data;

        } catch (IOException e) {
            log.error("加载测试数据时发生IO错误", e);
            throw new ModelException("数据加载失败", e);
        }
    }

    // 以下方法与之前保持一致
    private List<String> getAndValidatePredefinedFields() {
        PreprocessorParam.FeatureConfig config = preprocessorParam.getConfig();

        if (config == null) {
            throw new ModelException("预定义配置中的FeatureConfig为null", "NULL_FEATURE_CONFIG");
        }

        List<String> numericFields = cleanAndValidateFieldList(
                config.getNumCols(), "数值型字段(num_cols)");

        List<String> categoricalFields = cleanAndValidateFieldList(
                config.getCatCols(), "分类型字段(cat_cols)");

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
        log.debug("预定义字段加载完成 | 数值型: {}, 分类型: {}, 总计: {}",
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
}
