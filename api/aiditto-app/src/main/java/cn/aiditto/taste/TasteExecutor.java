package cn.aiditto.taste;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 画像提取专用执行器。包成自定义类型而不是直接暴露 Executor Bean，
 * 避免触发 Spring Boot 的 @ConditionalOnMissingBean(Executor.class) 而顶掉全局 applicationTaskExecutor。
 *
 * async-enabled=false 时退化为同步（Runnable::run），供集成测试消除异步竞态。
 */
public final class TasteExecutor implements DisposableBean {

    private final Executor delegate;
    private final ThreadPoolTaskExecutor pool;

    public TasteExecutor(boolean async) {
        if (!async) {
            this.pool = null;
            this.delegate = Runnable::run;
            return;
        }
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("taste-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(10);
        ex.initialize();
        this.pool = ex;
        this.delegate = ex;
    }

    public void execute(Runnable task) {
        delegate.execute(task);
    }

    public boolean async() {
        return pool != null;
    }

    @Override
    public void destroy() {
        if (pool != null) pool.shutdown();
    }
}
