package cn.aiditto.recall;

import java.util.List;

/**
 * 向量召回引擎抽象。业务层只依赖这个接口，换引擎（Qdrant → Milvus）不改 RecallPipeline。
 * 结构化路（R3/R4/R5）走 StructuredRecallService，Qdrant 不可用时仍可独立工作。
 */
public interface RecallService {

    String engine();

    /** 幂等：collection 不存在才建，已存在则跳过（含 payload index）。 */
    void ensureCollection();

    void upsert(List<RecallModel.Point> points);

    List<RecallModel.Hit> taste(float[] vec, RecallModel.Filter filter, int limit, int hnswEf);

    List<RecallModel.Hit> persona(float[] vec, RecallModel.Filter filter, int limit, int hnswEf);

    /** 注销 / 拉黑必须真删，不能只靠 filter。 */
    void delete(List<Long> userIds);
}
