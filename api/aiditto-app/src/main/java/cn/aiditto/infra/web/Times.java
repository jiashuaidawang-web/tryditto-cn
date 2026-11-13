package cn.aiditto.infra.web;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** API timestamps are ISO-8601 UTC with milliseconds: 2026-11-18T11:00:00.000Z (技术方案 §6.1). */
public final class Times {
    private static final DateTimeFormatter ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Times() {}

    public static String iso(Instant t) {
        return t == null ? null : ISO_MILLIS.format(t);
    }

    /** DATETIME(3) keeps milliseconds; truncate before writing so read-back compares equal. */
    public static Instant millis(Instant t) {
        return t.truncatedTo(ChronoUnit.MILLIS);
    }
}
