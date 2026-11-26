package cn.aiditto.domain.relationship;

import java.util.List;
import java.util.Map;

/**
 * Internal extraction context. {@code exportCanonical} is the only document a model may see.
 * The answer map and statements stay on the server and are not part of that document.
 */
public record ProfileContext(
        long userId,
        int sourceProfileVersion,
        long generationEpoch,
        boolean processingGranted,
        boolean modelExportGranted,
        String featureRegistryVersion,
        String promptVersion,
        List<Item> items,
        String exportCanonical,
        String inputHash,
        Map<String, Object> answers,
        List<ProfileContextBuilder.Statement> statements,
        List<ProfileContextBuilder.ScenarioChoice> scenarios) {

    public ProfileContext {
        items = List.copyOf(items);
        answers = Map.copyOf(answers);
        statements = List.copyOf(statements);
        scenarios = List.copyOf(scenarios);
        if (exportCanonical == null || inputHash == null || inputHash.length() != 64) {
            throw new IllegalArgumentException("context hash");
        }
    }

    public String fingerprintToken() {
        StringBuilder out = new StringBuilder("f");
        for (int i = 0; i < 16; i++) {
            int digit = Character.digit(inputHash.charAt(i), 16);
            out.append((char) ('a' + Math.max(0, digit)));
        }
        return out.toString();
    }

    public Item item(String token) {
        for (Item item : items) {
            if (item.token().equals(token)) return item;
        }
        return null;
    }

    public record Item(String token, String kind, String featureCode, String value, String text) {}
}
