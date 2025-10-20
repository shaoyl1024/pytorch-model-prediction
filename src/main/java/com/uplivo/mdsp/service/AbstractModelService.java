package com.uplivo.mdsp.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.uplivo.mdsp.common.exception.ModelException;
import com.uplivo.mdsp.config.model.ModelConfigManager;
import com.uplivo.mdsp.config.model.ModelContext;
import com.uplivo.mdsp.core.preprocessor.deepfm.base.AbstractPreprocessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description 模型服务抽象基类：模板方法定义推理流程
 *
 * @Author charles
 * @Date 2025/9/5 23:48
 * @Version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractModelService {

    /**
     * ONNX运行时环境（单例，负责管理ONNX的底层资源）
     */
    private final OrtEnvironment ortEnvironment;

    /**
     * ONNX模型配置（管理多版本模型上下文，由Spring注入）
     */
    private final ModelConfigManager modelConfigManager;

    // ============================================================================
    // 抽象方法：子类必须实现的差异化逻辑
    // ============================================================================

    /**
     * 获取当前模型的预处理器（与模型版本绑定）
     */
    protected abstract AbstractPreprocessor getPreprocessor();

    /**
     * 获取当前模型的版本标识（需与OnnxConfig中配置一致）
     */
    protected abstract String getModelVersion();


    /**
     * 模板方法：固定预测全流程（子类不可重写）
     * <p>通用预测入口：接收原始数据，返回预测结果</p>
     *
     * @param rawData 原始特征列表（每条数据为Map<String, String>）
     * @return 预测结果数组（与输入数据顺序一致）
     * @throws ModelException 流程异常时统一抛出
     */
    public final float[] predict(List<Map<String, String>> rawData) throws ModelException {
        try {
            if (rawData == null || rawData.isEmpty()) {
                throw new IllegalArgumentException("Raw data cannot be null or empty");
            }

            String modelVersion = getModelVersion();
            log.info("Model [{}] start prediction - Sample count: {}", modelVersion, rawData.size());

            // 特征预处理（子类实现）
            float[][] processedFeatures = getPreprocessor().batchPreprocess(rawData);
            log.info("Model [{}] preprocessing completed - Feature shape: {}×{}",
                    modelVersion, processedFeatures.length, processedFeatures[0].length);

            // 模型推理（子类实现核心逻辑，父类提供工具方法）
            float[] predictionResults = doPredict(processedFeatures);

            log.info("Model [{}] prediction completed", modelVersion);
            return predictionResults;

        } catch (Exception e) {
            log.error("Model [{}] prediction process failed - Unexpected error", getModelVersion(), e);
            throw new ModelException("Model [" + getModelVersion() + "] prediction process failed", e);
        }
    }

    /**
     * 模型通用推理逻辑实现
     *
     * @param features 预处理后的特征矩阵，形状为 [样本数, 特征维度]
     * @return 模型预测结果数组，长度与输入样本数一致
     * @throws ModelException
     */
    protected float[] doPredict(float[][] features) throws ModelException {
        // 输入校验（复用父类工具方法）
        validateInputFeatures(features);
        int batchSize = features.length;
        int featureDim = features[0].length;
        String modelVersion = getModelVersion(); // 获取当前版本
        log.info("Batch prediction started - Model version: {}, Sample count: {}, Feature dimension: {}",
                modelVersion, batchSize, featureDim);

        // 获取模型上下文（父类已处理空指针，直接使用）
        ModelContext modelContext = getModelContext();
        OrtSession targetSession = modelContext.getSession();
        String inputNodeName = modelContext.getInputNodeName();
        String outputNodeName = modelContext.getOutputNodeName();
        log.info("Using model resources - Session: {}, Input node: {}, Output node: {}",
                targetSession.hashCode(), inputNodeName, outputNodeName);

        // 准备输入数据（FloatBuffer）
        FloatBuffer inputBuffer = prepareInputBuffer(features, batchSize, featureDim);

        // 创建输入张量并执行推理（try-with-resources确保资源释放）
        try (OnnxTensor inputTensor = createOnnxTensor(inputBuffer, new long[]{batchSize, featureDim})) {
            // 构建输入映射（仅包含目标输入节点）
            Map<String, OnnxTensor> inputs = Collections.singletonMap(inputNodeName, inputTensor);

            // 执行推理（仅获取目标输出节点，减少内存占用）
            try (OrtSession.Result inferenceResult = targetSession.run(inputs, Collections.singleton(outputNodeName))) {
                // 解析结果（复用父类工具方法，自动适配输出格式）
                return parseInferenceResult(inferenceResult, batchSize);
            }
        } catch (OrtException e) {
            log.error("ONNX inference failed - Model version: {}", modelVersion, e);
            throw new ModelException("Model inference failed (version: " + modelVersion + ")", e);
        }
    }


    // ============================================================================
    // 父类提供的工具方法（子类可直接调用）
    // ============================================================================

    /**
     * 验证特征矩阵合法性（维度一致性等）
     */
    protected void validateInputFeatures(float[][] features) {
        if (features == null || features.length == 0) {
            throw new IllegalArgumentException("Feature matrix cannot be null or empty");
        }
        int expectedDim = features[0].length;
        for (int i = 1; i < features.length; i++) {
            if (features[i].length != expectedDim) {
                throw new IllegalArgumentException("Feature dimension mismatch at sample " + i);
            }
        }
    }

    /**
     * 准备ONNX输入缓冲区（将二维特征转为FloatBuffer）
     */
    protected FloatBuffer prepareInputBuffer(float[][] features, int batchSize, int featureDim) {
        FloatBuffer buffer = FloatBuffer.allocate(batchSize * featureDim);
        for (float[] sample : features) {
            buffer.put(sample);
        }
        buffer.rewind();
        return buffer;
    }


    /**
     * 创建ONNX输入张量（兼容不同版本API）
     */
    protected OnnxTensor createOnnxTensor(FloatBuffer inputBuffer, long[] inputShape) throws OrtException {
        try {
            return OnnxTensor.createTensor(ortEnvironment, inputBuffer, inputShape);
        } catch (OrtException e) {
            log.error("Fallback to float[] mode for tensor creation: {}", e.getMessage());
            float[] inputArray = new float[inputBuffer.capacity()];
            inputBuffer.rewind();
            inputBuffer.get(inputArray);
            return OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(inputArray), inputShape);
        }
    }

    /**
     * 获取模型上下文（会话+节点名）
     */
    protected ModelContext getModelContext() {
        String version = getModelVersion();
        ModelContext context = modelConfigManager.getModelContext(version);
        if (context == null || context.getSession() == null) {
            log.error("Model context not initialized for version: {}", version);
            return null;
        }
        return context;
    }

    /**
     * 解析推理结果张量，提取预测值数组（兼容一维/二维输出格式）
     *
     * <p>支持两种常见模型输出格式：
     * <li>二维数组 [batchSize, 1]：多数分类/回归模型的标准输出</li>
     * <li>一维数组 [batchSize]：部分轻量化模型的简化输出</li>
     *
     * @param inferenceResult   ONNX推理结果对象（包含输出张量）
     * @param expectedBatchSize 预期样本数量（需与输入特征矩阵的样本数一致）
     * @return 解析后的预测值数组（长度 = expectedBatchSize）
     * @throws OrtException   张量值获取失败（如底层ONNX Runtime错误）
     * @throws ModelException 结果格式不支持或数量不匹配时抛出
     */
    protected float[] parseInferenceResult(OrtSession.Result inferenceResult, int expectedBatchSize)
            throws OrtException, ModelException {
        // 获取输出张量（默认取第一个输出节点，符合多数模型设计）
        OnnxTensor outputTensor = (OnnxTensor) inferenceResult.get(0);
        String modelVersion = getModelVersion();
        Object outputValue = outputTensor.getValue(); // 统一获取输出值，避免重复调用

        // 根据输出值类型分发处理（主动判断类型，替代原异常捕获逻辑，更高效）
        if (outputValue instanceof float[][]) {
            return parse2DOutput((float[][]) outputValue, expectedBatchSize, modelVersion);
        } else if (outputValue instanceof float[]) {
            return parse1DOutput((float[]) outputValue, expectedBatchSize, modelVersion);
        } else {
            // 不支持的输出类型（提前报错，避免后续逻辑异常）
            throw new ModelException(String.format(
                    "Unsupported output type for version %s - Expected float[][] or float[], but got %s",
                    modelVersion,
                    outputValue.getClass().getSimpleName()
            ));
        }
    }

    /**
     * 解析二维输出张量 [batchSize, 1]
     *
     * @param output2D          二维输出数组
     * @param expectedBatchSize 预期样本数
     * @param modelVersion      模型版本（用于异常信息）
     * @return 提取的预测值数组
     * @throws ModelException 数量不匹配或单样本结果为空时抛出
     */
    private float[] parse2DOutput(float[][] output2D, int expectedBatchSize, String modelVersion) throws ModelException {
        // 校验样本数量匹配
        if (output2D.length != expectedBatchSize) {
            throw new ModelException(buildMismatchMsg(expectedBatchSize, output2D.length, modelVersion));
        }

        // 提取每个样本的预测值（取第一列，兼容[batch,1]格式）
        float[] predictions = new float[expectedBatchSize];
        for (int i = 0; i < expectedBatchSize; i++) {
            if (output2D[i] == null || output2D[i].length == 0) {
                throw new ModelException(String.format(
                        "Empty prediction for sample %d (Model version: %s)",
                        i,
                        modelVersion
                ));
            }
            predictions[i] = output2D[i][0];
        }
        return predictions;
    }

    /**
     * 解析一维输出张量 [batchSize]
     *
     * @param output1D          一维输出数组
     * @param expectedBatchSize 预期样本数
     * @param modelVersion      模型版本（用于异常信息）
     * @return 直接返回一维数组（已校验长度）
     * @throws ModelException 数量不匹配时抛出
     */
    private float[] parse1DOutput(float[] output1D, int expectedBatchSize, String modelVersion) throws ModelException {
        if (output1D.length != expectedBatchSize) {
            throw new ModelException(buildMismatchMsg(expectedBatchSize, output1D.length, modelVersion));
        }
        return output1D;
    }

    /**
     * 构建数量不匹配的异常消息（复用逻辑，避免重复编码）
     */
    private String buildMismatchMsg(int expected, int actual, String version) {
        return String.format(
                "Prediction count mismatch - Input: %d, Output: %d (Model version: %s)",
                expected,
                actual,
                version
        );
    }

    /**
     * 获取模型信息
     */
    public Map<String, Object> getModelInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            ModelContext context = getModelContext();
            info.put("modelId", context.getModelId());
            info.put("modelName", context.getModelName());
            info.put("modelVersion", context.getModelVersion());
            info.put("modelType", context.getModelType());
            info.put("inputNode", context.getInputNodeName());
            info.put("outputNode", context.getOutputNodeName());
            info.put("enabled", context.isEnabled());
            info.put("valid", context.isValid());
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        return info;
    }

}
