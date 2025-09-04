package com.example.demo.service;

import com.example.demo.model.PredictionResult;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description 测试数据加载服务类，负责加载和解析模型预测所需的测试数据
 * @Author charles
 * @Date 2025/9/3 23:04
 * @Version 1.0.0
 */
@Service
@Slf4j
public class TestDataLoaderService {
    @Value("${model.onnx.test-data-path}")
    private Resource testDataResource;

    private static final String[] NUM_COLS = {"I1", "I2", "I3", "I4", "I5", "I6", "I7", "I8", "I9", "I10", "I11", "I12", "I13"};
    private static final String[] CAT_COLS = {"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C21", "C22", "C23", "C24", "C25", "C26"};
    private static final char TSV_SEPARATOR = '\t';

    public List<Map<String, String>> loadTestData(int batchSize) throws IOException, CsvValidationException {
        List<Map<String, String>> rawSamples = new ArrayList<>(batchSize);

        try (InputStreamReader inputStreamReader = new InputStreamReader(
                testDataResource.getInputStream(),
                StandardCharsets.UTF_8)) {

            CSVParser csvParser = new CSVParserBuilder()
                    .withSeparator(TSV_SEPARATOR)
                    .withIgnoreQuotations(true)
                    .build();

            try (CSVReader reader = new CSVReaderBuilder(inputStreamReader)
                    .withCSVParser(csvParser)
                    .withSkipLines(0)
                    .build()) {

                String[] nextLine;
                int count = 0;
                boolean isFirstLine = false;

                while ((nextLine = reader.readNext()) != null) {
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }

                    if (count >= batchSize) {
                        break;
                    }

                    Map<String, String> rawSample = new HashMap<>();
                    for (int i = 0; i < NUM_COLS.length; i++) {
                        rawSample.put(NUM_COLS[i], i < nextLine.length ? nextLine[i] : "");
                    }
                    for (int i = 0; i < CAT_COLS.length; i++) {
                        int colIndex = NUM_COLS.length + i;
                        rawSample.put(CAT_COLS[i], colIndex < nextLine.length ? nextLine[colIndex] : "");
                    }
                    rawSample.put("sampleId", "sample_" + (count + 1));

                    rawSamples.add(rawSample);
                    count++;
                }

                log.info("Loaded test data: {} samples", rawSamples.size());
                return rawSamples;
            }
        }
    }

    public List<PredictionResult> wrapPredictionResult(List<Map<String, String>> rawSamples, double[] ctrProbs) {
        List<PredictionResult> results = new ArrayList<>(rawSamples.size());
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < rawSamples.size(); i++) {
            Map<String, String> rawSample = rawSamples.get(i);
            String sampleId = rawSample.getOrDefault("sampleId", "unknown_" + i);
            String rawFeatures = rawSample.toString();
            double ctrProb = ctrProbs[i];

            results.add(new PredictionResult(sampleId, rawFeatures, ctrProb, now));
        }
        return results;
    }
}