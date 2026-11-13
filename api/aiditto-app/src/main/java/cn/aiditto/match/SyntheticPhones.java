package cn.aiditto.match;

import java.util.List;

/** Phones of the fixture. Synthetic numbers are fictional and must never be dialed or texted. */
public final class SyntheticPhones {
    public static final List<String> DEMO = List.of("13800000001", "13800000002", "13800000003", "13800000004");

    private SyntheticPhones() {}

    public static boolean isDemo(String phone) {
        return DEMO.contains(phone);
    }

    /** Stable 11-digit phone for fixture index {@code i} (0-based). Never overlaps the demo phones. */
    public static String fictional(int index) {
        return "137" + String.format("%08d", index + 1);
    }
}
