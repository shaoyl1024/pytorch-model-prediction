package com.uplivo.mdsp.core.preprocessor.deepfm.base;

import com.uplivo.mdsp.common.exception.ModelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * @Description 抽象预处理器服务父类
 * <p>设计思路：采用「模板方法模式」，封装预处理流程的公共逻辑（如批量处理、单样本流程骨架）</p>
 * <p>将差异化逻辑（如参数加载、特征列定义、特征处理规则）抽象为方法，由子类按需实现</p>
 * <p>实现“流程统一、细节定制”，避免重复编码并保证各预处理器行为一致性。</p>
 * @Author charles
 * @Date 2025/9/5 14:11
 * @Version 1.0.0
 */
@Component
@Slf4j
public abstract class AbstractPreprocessor {

    // ============================================================================
    // 公共常量：所有子类共享的固定配置，避免硬编码分散
    // ============================================================================
    /**
     * 未知/缺失值标记（分类特征低频值、数值特征缺失均用此标记）
     */
    protected static final String UNK_MARKER = "UNK";
    /**
     * Log1p计算下界（避免输入≤-1导致NaN，log1p(x)要求x>-1）
     */
    protected static final double LOG1P_LOWER_BOUND = -0.999;
    /**
     * 最小缩放因子（避免标准化时除以0，当标准差接近0时用此值替代）
     */
    protected static final double MIN_SCALE = 1e-9;


    // ============================================================================
    // 抽象方法：子类必须实现的差异化逻辑（预处理核心定制点）
    // ============================================================================

    /**
     * 初始化预处理参数（Spring生命周期方法）
     * 子类实现：从指定来源加载参数（如Pickle文件、JSON配置），初始化numericParams/categoricalParams
     *
     * @PostConstruct 注解确保Spring容器初始化子类Bean时自动执行，无需手动调用
     */
    @PostConstruct
    public abstract void initParams();

    /**
     * 获取数值特征列名列表
     * 子类实现：返回当前预处理器对应的所有数值特征名（需与原始数据字段名、参数配置一致）
     *
     * @return 数值特征列名列表（如 ["I1", "I2", "I3"]）
     */
    protected abstract List<String> getNumericColumns();

    /**
     * 获取分类特征列名列表
     * 子类实现：返回当前预处理器对应的所有分类特征名（需与原始数据字段名、参数配置一致）
     *
     * @return 分类特征列名列表（如 ["C1", "C2", "C3"]）
     */
    protected abstract List<String> getCategoricalColumns();

    /**
     * 根据数值特征名获取对应的预处理参数
     * 子类实现：从初始化后的参数集合中，匹配指定数值特征的参数（均值、中位数、缩放因子）
     *
     * @param numericCol 数值特征列名（如 "I1"）
     * @return 该特征的预处理参数DTO（NumericParam）
     */
    protected abstract BasePreprocessorParam.NumericParam getNumericParam(String numericCol);

    /**
     * 根据分类特征名获取对应的预处理参数
     * 子类实现：从初始化后的参数集合中，匹配指定分类特征的参数（高频值集合、编码映射、默认编码）
     *
     * @param categoricalCol 分类特征列名（如 "C1"）
     * @return 该特征的预处理参数DTO（CategoricalParam）
     */
    protected abstract BasePreprocessorParam.CategoricalParam getCategoricalParam(String categoricalCol);

    /**
     * 验证单条原始样本的合法性
     * 子类实现：定义样本必须包含的特征、特征值格式等规则（如缺失关键特征则抛异常）
     *
     * @param rawSample 原始样本（Map键为特征名，值为原始字符串）
     * @throws ModelException 样本不合法时抛出
     */
    protected abstract void validateSample(Map<String, String> rawSample);

    /**
     * 处理单条数值特征值
     * 子类实现：按参数定义的规则处理（如缺失值填充、Log1p转换、标准化）
     *
     * @param rawVal     原始数值特征值（可能为空字符串或"UNK"）
     * @param numericCol 数值特征列名（用于获取对应参数）
     * @return 处理后的浮点型特征值
     */
    protected abstract float processNumericFeature(String rawVal, String numericCol);

    /**
     * 处理单条分类特征值
     * 子类实现：按参数定义的规则处理（如高频值保留、低频值转UNK、编码映射）
     *
     * @param rawVal         原始分类特征值（可能为空字符串或低频值）
     * @param categoricalCol 分类特征列名（用于获取对应参数）
     * @return 处理后的整型编码（转为float存入特征数组，与数值特征类型统一）
     */
    protected abstract int processCategoricalFeature(String rawVal, String categoricalCol);


    // ============================================================================
    // 公共方法：父类实现的通用逻辑（子类直接复用，无需修改）
    // ============================================================================

    /**
     * 批量预处理原始样本
     * 核心逻辑：循环调用单样本处理方法，组装批量特征数组，保证样本顺序与输入一致
     *
     * @param rawSamples 原始样本列表（每条样本为Map<String, String>）
     * @return 预处理后的特征矩阵（shape: [样本数, 特征总维度]，float类型适配模型输入）
     */
    public float[][] batchPreprocess(List<Map<String, String>> rawSamples) {
        // 边界处理：空输入返回空数组，避免空指针
        if (rawSamples == null || rawSamples.isEmpty()) {
            log.info("Batch preprocessing: no raw samples provided, return empty feature array");
            return new float[0][0];
        }

        // 计算特征总维度（数值特征数 + 分类特征数）
        List<String> numCols = getNumericColumns();
        List<String> catCols = getCategoricalColumns();
        int totalFeatureDim = numCols.size() + catCols.size();
        int sampleCount = rawSamples.size();

        // 初始化批量特征数组（避免动态扩容，提升性能）
        float[][] batchFeatures = new float[sampleCount][totalFeatureDim];

        // 循环处理每条样本（保持输入输出顺序一致）
        for (int i = 0; i < sampleCount; i++) {
            batchFeatures[i] = singlePreprocess(rawSamples.get(i));
        }

        log.info("Batch preprocessing finished: sample count={}, total feature dimension={}",
                sampleCount, totalFeatureDim);
        return batchFeatures;
    }

    /**
     * 单样本预处理（模板方法：定义固定流程，步骤不可修改）
     * 流程：样本验证 → 数值特征处理 → 分类特征处理 → 组装特征数组
     *
     * @param rawSample 原始样本（Map键为特征名，值为原始字符串）
     * @return 单样本处理后的特征数组（float类型，顺序：数值特征在前，分类特征在后）
     */
    public float[] singlePreprocess(Map<String, String> rawSample) {
        // 步骤1：验证样本合法性（子类实现规则）
        validateSample(rawSample);

        // 步骤2：获取特征列列表，计算特征维度
        List<String> numCols = getNumericColumns();
        List<String> catCols = getCategoricalColumns();
        int totalFeatureDim = numCols.size() + catCols.size();
        float[] processedFeature = new float[totalFeatureDim];
        int featureIndex = 0; // 特征数组索引（用于按顺序填充）

        // 步骤3：处理数值特征（按列顺序填充）
        for (String numCol : numCols) {
            // 从原始样本获取特征值，无值则用空字符串（子类处理时转为UNK）
            String rawVal = rawSample.getOrDefault(numCol, "");
            processedFeature[featureIndex++] = processNumericFeature(rawVal, numCol);
        }

        // 步骤4：处理分类特征（按列顺序填充，接在数值特征后）
        for (String catCol : catCols) {
            String rawVal = rawSample.getOrDefault(catCol, "");
            processedFeature[featureIndex++] = processCategoricalFeature(rawVal, catCol);
        }

        return processedFeature;
    }

}
