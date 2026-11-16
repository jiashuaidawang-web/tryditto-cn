package cn.aiditto.taste;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;

/**
 * 入驻完成 / 已入驻用户编辑答案后，异步生成画像。
 *
 * AFTER_COMMIT + 专用线程池：不占用户请求时间、不读未提交数据、事务回滚不触发。
 * 任何异常都只记日志，绝不回抛影响入驻；队列满或执行失败的用户由 TasteBatchService 补齐。
 */
@Component
public class TasteListener {

    private static final Logger log = LoggerFactory.getLogger(TasteListener.class);

    private final TasteExtractor extractor;
    private final TasteExecutor executor;
    private final boolean enabled;

    public TasteListener(TasteExtractor extractor, TasteExecutor executor,
                         @Value("${aiditto.taste.enabled:true}") boolean enabled) {
        this.extractor = extractor;
        this.executor = executor;
        this.enabled = enabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProfileWritten(ProfileWrittenEvent event) {
        if (!enabled) return;
        Runnable task = () -> {
            try {
                TasteExtractor.Result r = extractor.ensure(event.userId(), event.trigger());
                log.info("taste_extract user={} outcome={} version={} reason={}",
                        event.userId(), r.outcome(), r.version(), r.reason());
            } catch (RuntimeException e) {
                log.warn("taste_extract failed user={} err={}", event.userId(), e.getClass().getSimpleName());
            }
        };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            log.warn("taste_extract queue full user={} (batch refresh will catch up)", event.userId());
        }
    }
}
