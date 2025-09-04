package com.example.demo.service;

import ai.onnxruntime.*;
import com.example.demo.config.OnnxConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PredictionService {

    private final OrtEnvironment ortEnvironment;
    private final OrtSession ortSession;
    private final OnnxConfig onnxConfig;

    public double[] predict(float[][] features) {
        if (features == null || features.length == 0) {
            return new double[0];
        }

        int batchSize = features.length;
        int featureDim = features[0].length;

        try {
            // 准备输入数据
            FloatBuffer buffer = FloatBuffer.allocate(batchSize * featureDim);
            for (float[] feature : features) {
                buffer.put(feature);
            }
            buffer.rewind();

            // 创建输入张量
            long[] shape = {batchSize, featureDim};
            OnnxTensor tensor = OnnxTensor.createTensor(ortEnvironment, buffer, shape);

            // 执行推理
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(onnxConfig.getInputNodeName(), tensor);

            try (OrtSession.Result result = ortSession.run(inputs)) {
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
            }

        } catch (OrtException e) {
            log.error("模型推理失败", e);
            throw new com.example.demo.exception.ModelException("模型推理失败", e);
        }
    }
}
