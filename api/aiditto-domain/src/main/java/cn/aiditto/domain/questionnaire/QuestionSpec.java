package cn.aiditto.domain.questionnaire;

import java.util.Set;

/**
 * One entry of questions.json (exported from web/src/onboarding/questions.ts). {@code options} is the union over
 * every {@code optionsFor} branch, so validation accepts any option the frontend could have shown.
 */
public record QuestionSpec(
        String id,
        QuestionType type,
        Set<String> options,
        Integer maxSelect,
        Integer maxLength,
        Set<String> exclusive,
        boolean optional,
        String emailKind,
        boolean allowCustom,
        boolean isPrivate,
        String hobby,
        Set<String> dropLangs) {

    public QuestionSpec {
        options = options == null ? Set.of() : Set.copyOf(options);
        exclusive = exclusive == null ? Set.of() : Set.copyOf(exclusive);
        dropLangs = dropLangs == null ? Set.of() : Set.copyOf(dropLangs);
    }

    public boolean droppedIn(String lang) {
        return dropLangs.contains(lang);
    }
}
