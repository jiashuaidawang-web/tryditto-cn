package cn.aiditto.domain.questionnaire;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The question bank as the backend sees it. Built by the app from questions.json; nothing here reads files. */
public final class QuestionCatalog {

    /** Answers that are not questions but steer the flow (web/src/onboarding/flow.ts: the school-email escape). */
    public static final String NO_SCHOOL_EMAIL = "noSchoolEmail";

    /** Stored encrypted (技术方案 §4.4, plus the two email answers per §9 加密范围). */
    public static final Set<String> SENSITIVE = Set.of(
            "expectedGender", "dealbreakers", "ethnicity", "expectedEthnicity", "expectedPosition", "position",
            "politics", "politicsImportance", "religion", "religionImportance", "schoolEmail", "personalEmail");

    private final Map<String, QuestionSpec> byId;

    public QuestionCatalog(Collection<QuestionSpec> specs) {
        Map<String, QuestionSpec> m = new LinkedHashMap<>();
        for (QuestionSpec s : specs) m.put(s.id(), s);
        this.byId = Collections.unmodifiableMap(m);
    }

    public Optional<QuestionSpec> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<QuestionSpec> all() {
        return byId.values();
    }

    public boolean isFlag(String id) {
        return NO_SCHOOL_EMAIL.equals(id);
    }

    public boolean isSensitive(String id) {
        return SENSITIVE.contains(id);
    }

    /** Branch question ids of a hobby, in bank order (BRANCHES in questions.ts). */
    public List<String> branchesOf(String hobby) {
        List<String> out = new ArrayList<>();
        for (QuestionSpec s : byId.values()) {
            if (hobby.equals(s.hobby())) out.add(s.id());
        }
        return out;
    }

    /** Hobby option ids that own branch questions, in bank order. */
    public List<String> branchHobbies() {
        List<String> out = new ArrayList<>();
        for (QuestionSpec s : byId.values()) {
            if (s.hobby() != null && !out.contains(s.hobby())) out.add(s.hobby());
        }
        return out;
    }

    /** Branch answers to drop when their hobby is no longer picked (flow.ts staleBranchKeys). */
    public List<String> staleBranchKeys(Collection<String> pickedHobbies, Collection<String> answeredIds) {
        List<String> out = new ArrayList<>();
        for (QuestionSpec s : byId.values()) {
            if (s.hobby() != null && !pickedHobbies.contains(s.hobby()) && answeredIds.contains(s.id())) out.add(s.id());
        }
        return out;
    }
}
