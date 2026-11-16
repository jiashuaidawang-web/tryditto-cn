package cn.aiditto.recall;

import java.util.List;

/** Qdrant filterable HNSW 实现；构造时不连网，调用期失败由上层降级。 */
public final class QdrantRecallService implements RecallService {

    private final QdrantRestClient client;
    private final RecallProperties props;

    public QdrantRecallService(QdrantRestClient client, RecallProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public String engine() {
        return "qdrant";
    }

    @Override
    public void ensureCollection() {
        String c = props.collection();
        if (!client.collectionExists(c)) {
            client.createCollection(c, props.dimension());
            client.createPayloadIndexes(c);
        }
    }

    @Override
    public void upsert(List<RecallModel.Point> points) {
        if (points.isEmpty()) return;
        ensureCollection();
        client.upsert(props.collection(), points, 50);
    }

    @Override
    public List<RecallModel.Hit> taste(float[] vec, RecallModel.Filter filter, int limit, int hnswEf) {
        return client.search(props.collection(), "taste", vec, filter, limit, hnswEf,
                RecallModel.Source.R1_TASTE);
    }

    @Override
    public List<RecallModel.Hit> persona(float[] vec, RecallModel.Filter filter, int limit, int hnswEf) {
        return client.search(props.collection(), "persona", vec, filter, limit, hnswEf,
                RecallModel.Source.R2_PERSONA);
    }

    @Override
    public void delete(List<Long> userIds) {
        if (!userIds.isEmpty()) client.delete(props.collection(), userIds);
    }

    /** 供 FLAT 精确对照读取全量向量（不服务在线）。 */
    public List<QdrantRestClient.VectorPoint> scroll(RecallModel.Filter filter) {
        return client.scroll(props.collection(), filter, 256);
    }

    public QdrantRestClient raw() {
        return client;
    }
}
