package com.example.demo.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.demo.config.ModelContext;
import com.example.demo.config.OnnxConfig;
import com.example.demo.exception.ModelException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description ONNX模型预测服务
 * <p>核心职责：</p>
 * <p>1. 封装ONNX Runtime的调用细节，提供统一的模型推理接口</p>
 * <p>2. 支持多模型版本（如v1、v2）的切换与管理</p>
 * <p>3. 处理输入张量创建、推理执行和输出结果解析的全流程</p>
 * <p>4. 兼容不同版本ONNX Runtime的API差异，提供降级策略</p>
 *
 * @Author charles
 * @Date 2025/9/3 22:44
 * @Version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PredictionService {

    // ============================================================================
    // 依赖注入：ONNX运行时环境和模型配置
    // ============================================================================
    /** ONNX运行时环境（单例，负责管理ONNX的底层资源） */
    private final OrtEnvironment ortEnvironment;

    /** ONNX模型配置类（提供不同版本模型的上下文信息：会话、输入输出节点名等） */
    private final OnnxConfig onnxConfig;


    // ============================================================================
    // 核心方法：批量预测（支持多模型版本）
    // ============================================================================
    /**
     * 批量预测接口：根据指定模型版本执行推理
     *
     * @param features 预处理后的特征矩阵（shape: [样本数, 特征维度]）
     * @param modelVersion 模型版本（如"v1"、"v2"），用于匹配对应的ONNX模型配置
     * @return 预测结果数组（每个元素对应一条输入样本的预测值，如CTR概率）
     * @throws OrtException ONNX Runtime相关异常（如张量创建失败、推理执行错误）
     * @throws ModelException 业务层异常（如模型上下文获取失败、输入输出不匹配）
     */
    public float[] predict(float[][] features, String modelVersion) throws OrtException, ModelException {
        // Step 1: 输入校验（避免空数据或无效格式）
        validateInputFeatures(features);
        int batchSize = features.length;
        int featureDim = features[0].length;
        log.info("Batch prediction started - Model version: {}, Sample count: {}, Feature dimension: {}",
                modelVersion, batchSize, featureDim);

        // Step 2: 获取目标模型的上下文（会话+节点名配置）
        ModelContext modelContext = getModelContext(modelVersion);
        OrtSession targetSession = modelContext.getSession();
        String inputNodeName = modelContext.getInputNodeName();
        String outputNodeName = modelContext.getOutputNodeName();
        log.debug("Using model resources - Session: {}, Input node: {}, Output node: {}",
                targetSession.hashCode(), inputNodeName, outputNodeName);

        // Step 3: 准备输入数据（转换为ONNX张量所需的FloatBuffer）
        FloatBuffer inputBuffer = prepareInputBuffer(features, batchSize, featureDim);

        // Step 4: 创建输入张量并执行推理
        try (OnnxTensor inputTensor = createOnnxTensor(inputBuffer, batchSize, featureDim)) {
            // 构建输入映射（节点名→张量）
            Map<String, OnnxTensor> inputs = Collections.singletonMap(inputNodeName, inputTensor);

            // 执行推理（仅获取目标输出节点，提升效率）
            try (OrtSession.Result inferenceResult = targetSession.run(inputs, Collections.singleton(outputNodeName))) {
                // 解析输出张量并返回结果
                return parseInferenceResult(inferenceResult, batchSize, modelVersion);
            }
        } catch (OrtException e) {
            log.error("ONNX inference failed - Model version: {}", modelVersion, e);
            throw new ModelException("Model inference failed (version: " + modelVersion + ")", e);
        }
    }


    // ============================================================================
    // 私有工具方法：输入处理与校验
    // ============================================================================
    /**
     * 验证输入特征矩阵的合法性
     * @param features 待验证的特征矩阵
     * @throws IllegalArgumentException 输入为空或格式无效时抛出
     */
    private void validateInputFeatures(float[][] features) {
        if (features == null || features.length == 0) {
            throw new IllegalArgumentException("Input feature matrix cannot be null or empty");
        }
        if (features[0] == null || features[0].length == 0) {
            throw new IllegalArgumentException("Feature dimension cannot be zero");
        }
        // 校验所有样本的特征维度一致
        int expectedDim = features[0].length;
        for (int i = 1; i < features.length; i++) {
            if (features[i].length != expectedDim) {
                throw new IllegalArgumentException("Feature dimension mismatch - Sample " + i +
                        " has " + features[i].length + " dimensions (expected: " + expectedDim + ")");
            }
        }
    }

    /**
     * 将特征矩阵转换为ONNX张量所需的FloatBuffer
     * @param features 特征矩阵
     * @param batchSize 样本数量
     * @param featureDim 特征维度
     * @return 扁平化的FloatBuffer（按行优先顺序存储）
     */
    private FloatBuffer prepareInputBuffer(float[][] features, int batchSize, int featureDim) {
        int totalElements = batchSize * featureDim;
        FloatBuffer buffer = FloatBuffer.allocate(totalElements);

        // 按行填充缓冲区（保持与模型训练时的数据格式一致）
        for (float[] sample : features) {
            buffer.put(sample);
        }
        buffer.rewind(); // 重置指针至起始位置，确保张量读取正确

        return buffer;
    }


    // ============================================================================
    // 私有工具方法：ONNX张量操作
    // ============================================================================
    /**
     * 创建ONNX输入张量（兼容不同版本的ONNX Runtime API）
     * @param inputBuffer 输入数据缓冲区
     * @param batchSize 样本数量
     * @param featureDim 特征维度
     * @return 创建的OnnxTensor实例（需在使用后关闭以释放资源）
     * @throws OrtException 张量创建失败时抛出
     */
    private OnnxTensor createOnnxTensor(FloatBuffer inputBuffer, int batchSize, int featureDim) throws OrtException {
        long[] inputShape = new long[]{batchSize, featureDim};
        try {
            // 优先使用推荐的FloatBuffer构造方法（ONNX Runtime 1.16+支持）
            return OnnxTensor.createTensor(ortEnvironment, inputBuffer, inputShape);
        } catch (OrtException e) {
            // 降级策略：兼容旧版本API（将Buffer转为float[]）
            log.warn("Failed to create tensor with FloatBuffer, falling back to float[] mode: {}", e.getMessage());
            float[] inputArray = new float[inputBuffer.capacity()];
            inputBuffer.rewind();
            inputBuffer.get(inputArray);
            return OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(inputArray), inputShape);
        }
    }


    // ============================================================================
    // 私有工具方法：模型上下文与结果解析
    // ============================================================================
    /**
     * 获取指定版本模型的上下文信息（会话+节点名）
     * @param modelVersion 模型版本
     * @return 模型上下文对象
     * @throws ModelException 模型版本不存在或上下文为空时抛出
     */
    private ModelContext getModelContext(String modelVersion) {
        ModelContext context = onnxConfig.getModelContext(modelVersion);
        if (context == null) {
            log.error("Model context not found for version: {}", modelVersion);
            throw new ModelException("Model configuration not found (version: " + modelVersion + ")");
        }
        if (context.getSession() == null) {
            log.error("ONNX session is null for model version: {}", modelVersion);
            throw new ModelException("Model session not initialized (version: " + modelVersion + ")");
        }
        return context;
    }

    /**
     * 解析推理结果张量，提取预测值数组
     * @param inferenceResult ONNX推理结果对象
     * @param expectedBatchSize 预期的样本数量（与输入一致）
     * @param modelVersion 模型版本（用于错误日志）
     * @return 解析后的预测值数组
     * @throws OrtException 张量值获取失败时抛出
     * @throws ModelException 结果格式异常或数量不匹配时抛出
     */
    private float[] parseInferenceResult(OrtSession.Result inferenceResult, int expectedBatchSize, String modelVersion)
            throws OrtException, ModelException {

        // 获取输出张量（假设结果列表中只有一个目标输出节点）
        OnnxTensor outputTensor = (OnnxTensor) inferenceResult.get(0);

        try {
            // 尝试解析为二维数组 [batchSize, 1]（多数分类/回归模型的输出格式）
            float[][] output2D = (float[][]) outputTensor.getValue();

            // 校验样本数量匹配
            if (output2D.length != expectedBatchSize) {
                throw new ModelException("Prediction result count mismatch - Input: " + expectedBatchSize +
                        ", Output: " + output2D.length + " (Model version: " + modelVersion + ")");
            }

            // 提取每个样本的预测值（取第一列）
            float[] predictions = new float[expectedBatchSize];
            for (int i = 0; i < expectedBatchSize; i++) {
                if (output2D[i] == null || output2D[i].length == 0) {
                    throw new ModelException("Empty prediction for sample " + i + " (Model version: " + modelVersion + ")");
                }
                predictions[i] = output2D[i][0];
            }
            return predictions;

        } catch (ClassCastException e) {
            // 兼容一维数组输出格式 [batchSize]
            log.debug("Output tensor is 1D array, using direct parsing (Model version: {})", modelVersion);
            float[] output1D = (float[]) outputTensor.getValue();

            if (output1D.length != expectedBatchSize) {
                throw new ModelException("Prediction result count mismatch - Input: " + expectedBatchSize +
                        ", Output: " + output1D.length + " (Model version: " + modelVersion + ")");
            }
            return output1D;
        }
    }
}
