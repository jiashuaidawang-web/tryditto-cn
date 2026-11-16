package cn.aiditto.taste;

/** profile 行写完后发布；AFTER_COMMIT 才消费，保证读到的 answers/profile 是已提交的最新值。 */
public record ProfileWrittenEvent(long userId, String trigger) {}
