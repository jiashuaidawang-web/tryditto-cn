package cn.aiditto.domain.relationship;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Frozen live-profile-v2. The development split may be inspected while designing a stage.
 * The holdout split is for one final measurement and is not a tuning set.
 */
public final class ProfileLiveEvalV2 {
    public static final String VERSION = "live-profile-v2";
    public static final List<String> HOLDOUT_SMOKE_IDS = List.of("H001", "H011", "H041", "H071", "H067");

    private ProfileLiveEvalV2() {}

    public record Sample(String id, String split, String category, String disposition, String text,
                         String expected, String note) {
        public List<String> expectedKeys() {
            if (expected == null || expected.isBlank()) return List.of();
            List<String> keys = new ArrayList<>();
            for (String key : expected.split(";")) {
                if (!key.isBlank()) keys.add(key.strip());
            }
            return List.copyOf(keys);
        }

        public boolean withheld() {
            return "WITHHOLD".equals(disposition);
        }
    }

    public static String text() {
        try (var in = ProfileLiveEvalV2.class.getResourceAsStream("/relationship/live-profile-v2.tsv")) {
            if (in == null) throw new IllegalStateException("live-profile-v2 missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip() + "\n";
        } catch (IOException e) {
            throw new IllegalStateException("live-profile-v2 unreadable");
        }
    }

    public static String datasetHash() {
        return EvidenceIds.sha256(VERSION + "\n" + text());
    }

    public static List<Sample> samples() {
        List<Sample> out = new ArrayList<>();
        for (String line : text().strip().split("\n")) {
            String[] part = line.split("\t", -1);
            if (part.length != 7) throw new IllegalStateException("live-profile-v2 row");
            out.add(new Sample(part[0], part[1], part[2], part[3], part[4], part[5], part[6]));
        }
        return List.copyOf(out);
    }

    public static List<Sample> development() {
        return samples().stream().filter(sample -> "dev".equals(sample.split())).toList();
    }

    public static List<Sample> holdout() {
        return samples().stream().filter(sample -> "holdout".equals(sample.split())).toList();
    }
}
