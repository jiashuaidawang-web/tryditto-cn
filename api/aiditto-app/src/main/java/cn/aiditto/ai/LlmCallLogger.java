package cn.aiditto.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 调用日志接口。生产写 llm_call_log，测试用内存实现断言重试/切换/熔断。 */
public interface LlmCallLogger {

    record Entry(long id, Long weekId, String callPoint, String model, String promptVersion,
                 Integer inputTokens, Integer outputTokens, long latencyMs,
                 String status, String errorCode) {}

    void log(Entry entry);

    /** 内存实现，仅用于单元测试。 */
    final class InMemory implements LlmCallLogger {
        private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void log(Entry entry) {
            entries.add(entry);
        }

        public List<Entry> entries() {
            return List.copyOf(entries);
        }

        public long count(String status) {
            return entries.stream().filter(e -> e.status().equals(status)).count();
        }
    }
}
