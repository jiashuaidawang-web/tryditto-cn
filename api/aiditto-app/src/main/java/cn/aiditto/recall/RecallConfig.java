package cn.aiditto.recall;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 召回层装配。构造期不连 Qdrant / embedding 服务，线上容器无这些依赖也能正常启动。 */
@Configuration
@EnableConfigurationProperties({RecallProperties.class, VisionProperties.class})
public class RecallConfig {

    /** 视觉审美编码：未配置 key 时仍注册 bean（live()=false），构造期不连网，线上无 key 也能启动。 */
    @Bean
    VisionEmbeddingService visionEmbeddingService(VisionProperties p) {
        return new VisionEmbeddingService(p.version(), p.baseUrl(), p.apiKey(), p.model(), p.timeoutMs());
    }

    @Bean
    EmbeddingService embeddingService(RecallProperties p) {
        if (p.liveEmbedding()) {
            // 版本号必须带真实维度：换模型/换维度后旧向量自动被 filter 排除，禁止跨版本混算相似度
            String version = (p.embeddingVersion() == null || p.embeddingVersion().isBlank()
                    || p.embeddingVersion().startsWith("hash-"))
                    ? p.embeddingModel() + "-d" + p.dimension()
                    : p.embeddingVersion();
            return new OpenAiCompatEmbeddingService(version, p.embeddingBaseUrl(),
                    p.embeddingApiKey(), p.embeddingModel(), p.dimension(),
                    p.embeddingSendDimensions(), p.embeddingTimeoutMs());
        }
        return new HashingEmbeddingService(p.dimension());
    }

    @Bean
    QdrantRestClient qdrantRestClient(RecallProperties p) {
        return new QdrantRestClient(p.qdrantUrl(), p.timeoutMs());
    }

    @Bean
    QdrantRecallService qdrantRecallService(QdrantRestClient client, RecallProperties p) {
        return new QdrantRecallService(client, p);
    }
}
