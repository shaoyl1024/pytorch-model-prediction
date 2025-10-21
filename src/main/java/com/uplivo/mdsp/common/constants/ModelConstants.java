package com.uplivo.mdsp.common.constants;

/**
 * @Description 模型相关常量定义
 * @Author charles
 * @Date 2025/10/17 16:46
 * @Version 1.0.0
 */
public class ModelConstants {

    // 模型版本常量
    public static final String CTR_V1 = "ctr_v1";
    public static final String CTR_V2 = "ctr_v2";

    // 未知/缺失值标记（分类特征低频值、数值特征缺失均用此标记）
    public static final String UNK_MARKER = "UNK";
    public static final double LOG1P_LOWER_BOUND = -0.999;
    public static final double MIN_SCALE = 1e-9;
    public static final float PREDICTION_FAILURE_SCORE = -1.0f;

    // 模型输入输出节点默认名
    public static final String DEFAULT_INPUT_NODE = "input";
    public static final String DEFAULT_OUTPUT_NODE = "output";
}
