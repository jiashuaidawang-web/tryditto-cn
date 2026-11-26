package cn.aiditto.ai;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 确定性 Mock：同一输入永远同一输出，可按顺序注入故障。
 * 让本机无密钥、无网络也能验证主备切换、重试、熔断与兜底（技术方案 §8.3 / §14 测试分层）。
 */
public final class MockLlmModel implements LlmModel {

    public enum Fault {
        TIMEOUT, RATE_LIMIT, SERVER_ERROR, AUTH, EMPTY_CONTENT, BAD_JSON, EMPTY
    }

    private final String name;
    private final Deque<Fault> faults = new ConcurrentLinkedDeque<>();
    private final List<LlmRequest> calls = new CopyOnWriteArrayList<>();
    /** 按 callPoint 返回固定内容，供集成测试把「合法模型输出」注入整条生产链路。 */
    private final Map<String, String> scripts = new ConcurrentHashMap<>();
    private final AtomicReference<Runnable> beforeCall = new AtomicReference<>();

    public MockLlmModel(String name) {
        this.name = name;
    }

    public MockLlmModel enqueue(Fault... sequence) {
        if (sequence != null) for (Fault f : sequence) faults.add(f);
        return this;
    }

    /** 注册 / 覆盖某个调用点的确定性输出；传 null 清除。 */
    public MockLlmModel respond(String callPoint, String content) {
        if (content == null) scripts.remove(callPoint);
        else scripts.put(callPoint, content);
        return this;
    }

    public MockLlmModel clearScripts() {
        scripts.clear();
        return this;
    }

    /** Runs once on the next call, before a scripted reply or a fault. Tests use it to revoke a grant mid-flight. */
    public void beforeCall(Runnable hook) {
        beforeCall.set(hook);
    }

    public void clearFaults() {
        faults.clear();
        beforeCall.set(null);
    }

    public void clearCalls() {
        calls.clear();
    }

    public List<LlmRequest> calls() {
        return List.copyOf(calls);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Raw call(LlmRequest request) throws LlmException {
        calls.add(request);
        Runnable hook = beforeCall.getAndSet(null);
        if (hook != null) hook.run();
        Fault fault = faults.poll();
        if (fault != null) {
            switch (fault) {
                case TIMEOUT -> throw new LlmException(LlmException.Kind.TIMEOUT, name + " mock timeout");
                case RATE_LIMIT -> throw new LlmException(LlmException.Kind.RATE_LIMIT, name + " mock 429", "0");
                case SERVER_ERROR -> throw new LlmException(LlmException.Kind.SERVER_ERROR, name + " mock 5xx");
                case AUTH -> throw new LlmException(LlmException.Kind.AUTH, name + " mock 401");
                case EMPTY_CONTENT -> throw new LlmException(LlmException.Kind.EMPTY_CONTENT, "finish_reason=length");
                case BAD_JSON -> {
                    return new Raw("not-json```", "stop", request.user().length(), 12);
                }
                case EMPTY -> {
                    return new Raw("", "stop", request.user().length(), 0);
                }
            }
        }
        String scripted = scripts.get(request.callPoint());
        if (scripted != null) {
            return new Raw(scripted, "stop", request.user().length(), scripted.length());
        }
        String digest = Integer.toHexString((request.system() + "|" + request.user() + "|" + request.maxTokens()).hashCode());
        String content = "{\"mock\":true,\"digest\":\"" + digest + "\"}";
        return new Raw(content, "stop", request.user().length(), content.length());
    }
}
