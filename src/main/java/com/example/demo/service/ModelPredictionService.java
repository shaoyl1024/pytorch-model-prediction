package com.example.demo.service;

import ai.onnxruntime.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description ONNX模型预测服务
 * @Author charles
 * @Date 2025/9/3 22:44
 * @Version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModelPredictionService {

    private final OrtEnvironment ortEnvironment;
    private final OrtSession ortSession;

    /**
     * 批量预测：输入预处理后的特征数组，返回点击概率
     *
     * @param inputs 预处理后的特征（shape: [batchSize, 39]）
     * @return 点击概率数组
     */
    public double[] predictBatch(float[][] inputs) throws OrtException {
        // 1. 验证输入有效性
        if (inputs == null || inputs.length == 0) {
            throw new IllegalArgumentException("Input features cannot be empty");
        }
        int batchSize = inputs.length;
        int featureDim = inputs[0].length;
        log.info("Batch prediction - sample count: {}, feature dimension: {}", batchSize, featureDim);

        // 2. 准备输入数据（转换为FloatBuffer，ONNX Runtime推荐格式）
        // 2.1 计算总元素数
        int totalElements = batchSize * featureDim;
        // 2.2 创建FloatBuffer并填充数据
        FloatBuffer floatBuffer = FloatBuffer.allocate(totalElements);
        for (float[] sample : inputs) {
            floatBuffer.put(sample);
        }
        floatBuffer.rewind(); // 重置缓冲区指针

        // 3. 定义输入形状 [batchSize, 39]
        long[] inputShape = new long[]{batchSize, featureDim};

        // 4. 创建ONNX张量（关键修复：使用正确的方法重载）
        // 注意：根据ONNX Runtime版本，可能需要调整方法参数
        OnnxTensor inputTensor;
        try {
            // 版本1.16.x推荐用法：使用FloatBuffer创建张量
            inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, inputShape);
        } catch (OrtException e) {
            // 兼容旧版本：尝试使用float[]创建张量
            log.error("Failed to create tensor with FloatBuffer, attempting compatibility mode: {}", e.getMessage());
            float[] flatInputs = new float[totalElements];
            floatBuffer.get(flatInputs);
            inputTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(flatInputs), inputShape);
        }

        // 5. 构造模型输入（输入节点名必须与ONNX模型一致）
        Map<String, OnnxTensor> modelInputs = new HashMap<>();
        modelInputs.put("criteo_features", inputTensor); // 与Python导出的输入名一致

        // 6. 执行模型推理
        try (OrtSession.Result result = ortSession.run(modelInputs)) {
            // 7. 解析输出结果
            OnnxTensor outputTensor = (OnnxTensor) result.get(0); // 获取第一个输出节点

            // 正确代码：先获取二维数组，再提取为一维数组
            float[][] output2D = (float[][]) outputTensor.getValue();
            float[] outputProbs = new float[output2D.length];

            // 遍历二维数组，提取每个样本的预测概率（取每行第0列，因为输出是 [batch_size, 1]）
            for (int i = 0; i < output2D.length; i++) {
                // 确保输出维度正确（每行必须有1个元素）
                if (output2D[i] == null || output2D[i].length == 0) {
                    throw new RuntimeException("ONNX model output dimension abnormal - no prediction result for sample " + i);
                }
                outputProbs[i] = output2D[i][0]; // 提取当前样本的预测概率
            }
            // 8. 转换为double数组返回
            double[] ctrProbs = new double[outputProbs.length];
            for (int i = 0; i < outputProbs.length; i++) {
                ctrProbs[i] = outputProbs[i];
            }
            return ctrProbs;
        } finally {
            // 9. 释放资源
            if (inputTensor != null) {
                inputTensor.close();
            }
        }
    }
}