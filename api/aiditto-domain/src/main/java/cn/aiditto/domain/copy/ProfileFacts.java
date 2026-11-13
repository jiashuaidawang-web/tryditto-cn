package cn.aiditto.domain.copy;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The whitelisted facts about one person that copy may rest on (技术方案 §8.1). No gender, no expected
 * gender, no height, no contact info, no dealbreaker text. {@code nickname} is only used to detect
 * real names leaking into the output; it is never sent to a model.
 *
 * @param hobbies {@code hobbies} option ids, custom ones as {@code custom:文字}
 * @param hobbyOptions multi-choice branch answers by question id, e.g. {@code musicSound → {indie}}
 * @param hobbyTexts free-text branch answers by question id, e.g. {@code readingLastBook → 长安的荔枝}
 */
public record ProfileFacts(
        long userId,
        String nickname,
        boolean student,
        String schoolYear,
        Set<String> hobbies,
        Map<String, Set<String>> hobbyOptions,
        Map<String, String> hobbyTexts,
        Set<String> relationshipLooking,
        String matchingStyle,
        String budget,
        String firstMessage) {

    public ProfileFacts {
        hobbies = hobbies == null ? Set.of() : Set.copyOf(hobbies);
        relationshipLooking = relationshipLooking == null ? Set.of() : Set.copyOf(relationshipLooking);
        TreeMap<String, Set<String>> opts = new TreeMap<>();
        if (hobbyOptions != null) hobbyOptions.forEach((k, v) -> opts.put(k, Set.copyOf(v)));
        hobbyOptions = Collections.unmodifiableMap(opts);
        TreeMap<String, String> texts = new TreeMap<>();
        if (hobbyTexts != null) {
            hobbyTexts.forEach((k, v) -> {
                if (v != null && !v.isBlank()) texts.put(k, v.strip());
            });
        }
        hobbyTexts = Collections.unmodifiableMap(texts);
        firstMessage = firstMessage == null || firstMessage.isBlank() ? null : firstMessage.strip();
    }
}
