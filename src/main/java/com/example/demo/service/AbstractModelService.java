package com.example.demo.service;

import ai.onnxruntime.OrtException;
import com.example.demo.exception.ModelException;
import com.example.demo.preprocessor.AbstractPreprocessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * @Description 模型服务抽象基类
 * <p>设计模式：基于「模板方法模式」封装预测全流程的通用逻辑，将差异化细节（预处理器、模型版本）抽象为方法，</p>
 * <p>由子类按需实现。核心目标是：统一所有模型的预测流程规范，减少重复编码，同时保留子类的个性化扩展能力。</p>
 *
 * <p>通用流程固定为：输入校验 → 特征预处理 → 模型推理 → 结果返回，子类无需修改流程，仅需实现两个抽象方法即可接入。</p>
 * @Author charles
 * @Date 2025/9/5 23:48
 * @Version 1.0.0
 */
@Slf4j
public abstract class AbstractModelService {

    @Autowired
    protected PredictionService predictionService;

    /**
     * 模板方法：通用预测流程（子类不可重写，确保流程一致性）
     * 接收原始业务数据，经过固定流程处理后返回模型预测结果（如CTR概率）
     *
     * @param rawData 原始业务数据列表，每条数据为Map（key：特征名，value：原始特征值字符串）
     * @return 模型预测结果数组，每个元素对应一条原始数据的预测值（与输入列表顺序一致）
     * @throws ModelException 流程中发生任何异常（如预处理失败、推理失败）均封装为此异常抛出
     */
    public final float[] predict(List<Map<String, String>> rawData) throws ModelException {
        try {
            // Step 1: 输入校验（通用逻辑）- 避免空数据导致后续流程无意义执行
            if (rawData == null || rawData.isEmpty()) {
                throw new IllegalArgumentException("Raw data cannot be null or empty");
            }
            int sampleCount = rawData.size();
            log.info("Model [{}] start prediction - Sample count: {}", getModelVersion(), sampleCount);

            // Step 2: 特征预处理（子类实现差异化）- 调用子类指定的预处理器，将原始数据转为模型可识别的特征矩阵
            log.debug("Model [{}] start feature preprocessing", getModelVersion());
            float[][] processedFeatures = getPreprocessor().batchPreprocess(rawData);
            log.debug("Model [{}] preprocessing completed - Feature shape: {} samples × {} dimensions",
                    getModelVersion(), processedFeatures.length, processedFeatures[0].length);

            // Step 3: 模型推理（通用逻辑）- 调用公共预测服务，传入子类指定的模型版本，获取预测结果
            log.debug("Model [{}] start inference", getModelVersion());
            float[] predictionResults = predictionService.predict(processedFeatures, getModelVersion());

            // Step 4: 结果返回（通用逻辑）- 记录结果数量，确保与输入样本数匹配（日志仅警告，不中断流程，兼容特殊场景）
            if (predictionResults.length != sampleCount) {
                log.warn("Model [{}] prediction result count mismatch - Input samples: {}, Output results: {}",
                        getModelVersion(), sampleCount, predictionResults.length);
            }
            log.info("Model [{}] prediction completed - Return {} results", getModelVersion(), predictionResults.length);

            return predictionResults;

        } catch (OrtException e) {
            // 捕获ONNX推理专属异常（如会话失效、张量创建失败），封装业务异常并记录详细日志
            log.error("Model [{}] inference failed - ONNX runtime error", getModelVersion(), e);
            throw new ModelException("Model [" + getModelVersion() + "] inference failed", e);

        } catch (IllegalArgumentException e) {
            // 捕获输入参数异常（如空数据），直接封装抛出（无需额外堆栈，异常信息已明确）
            log.error("Model [{}] invalid input - {}", getModelVersion(), e.getMessage());
            throw new ModelException("Model [" + getModelVersion() + "] invalid input: " + e.getMessage(), e);

        } catch (Exception e) {
            // 捕获其他未知异常（如预处理逻辑错误），作为通用流程异常处理
            log.error("Model [{}] prediction process failed - Unexpected error", getModelVersion(), e);
            throw new ModelException("Model [" + getModelVersion() + "] prediction process failed", e);
        }
    }

    /**
     * 抽象方法：获取当前模型的专属预处理器
     * 子类实现要求：返回与当前模型匹配的预处理器实例（如CTR v1返回CtrV1Preprocessor），
     * 确保预处理逻辑与模型训练时的特征工程规则一致。
     *
     * @return 当前模型的专属预处理器（AbstractPreprocessor子类实例）
     */
    protected abstract AbstractPreprocessor getPreprocessor();

    /**
     * 抽象方法：获取当前模型的版本标识
     * 子类实现要求：返回与OnnxConfig中配置一致的版本号（如"v1"、"v2"），
     * 确保能匹配到对应的ONNX会话（OrtSession）和输入输出节点名。
     *
     * @return 当前模型的版本标识（非空字符串，需与OnnxConfig配置对齐）
     */
    protected abstract String getModelVersion();
}
