package cn.aiditto.domain.copy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What the validator checks copy against, built once per pair from the same facts the model sees.
 *
 * @param lowFields question ids where the lower-id person has a value
 * @param highFields same for the other person
 * @param sharedFields question ids where the two overlap (the program's "共同项清单"); {@code location}
 *     is always shared because every matched pair passed the distance rule
 * @param venueIds candidate venue ids; the chosen one must be among them
 * @param forbiddenNames real nicknames that must never appear (placeholders only)
 * @param knownTerms every text a quoted term may come from: free-text answers, option labels, venue names
 */
public record CopyContext(
        Set<String> lowFields,
        Set<String> highFields,
        Set<String> sharedFields,
        Set<String> venueIds,
        Set<String> forbiddenNames,
        List<String> knownTerms) {

    public static final String LOCATION = "location";

    public CopyContext {
        lowFields = Set.copyOf(lowFields);
        highFields = Set.copyOf(highFields);
        sharedFields = Set.copyOf(sharedFields);
        venueIds = Set.copyOf(venueIds);
        forbiddenNames = Set.copyOf(forbiddenNames);
        knownTerms = List.copyOf(knownTerms);
    }

    /** Builds the context for a pair; {@code otherNames} are extra nicknames to forbid (e.g. the rest of the batch). */
    public static CopyContext of(ProfileFacts low, ProfileFacts high, List<VenueOption> venues, Set<String> otherNames) {
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(high, "high");
        Set<String> names = new HashSet<>(otherNames == null ? Set.of() : otherNames);
        if (low.nickname() != null && !low.nickname().isBlank()) names.add(low.nickname().strip());
        if (high.nickname() != null && !high.nickname().isBlank()) names.add(high.nickname().strip());
        Set<String> venueIds = new LinkedHashSet<>();
        List<String> terms = new ArrayList<>();
        for (VenueOption v : venues) {
            venueIds.add(v.id());
            terms.add(v.name());
            terms.add(v.defaultActivity());
            terms.add(v.defaultExtra());
        }
        addTerms(low, terms);
        addTerms(high, terms);
        return new CopyContext(fields(low), fields(high), shared(low, high), venueIds, names, terms);
    }

    static Set<String> fields(ProfileFacts p) {
        Set<String> f = new HashSet<>();
        f.add("isStudent");
        f.add(LOCATION);
        if (p.student() && p.schoolYear() != null) f.add("schoolYear");
        if (!p.hobbies().isEmpty()) f.add("hobbies");
        p.hobbyOptions().forEach((q, v) -> {
            if (!v.isEmpty()) f.add(q);
        });
        f.addAll(p.hobbyTexts().keySet());
        if (!p.relationshipLooking().isEmpty()) f.add("relationshipLooking");
        if (p.matchingStyle() != null) f.add("matchingStyle");
        if (p.budget() != null) f.add("firstDateBudget");
        if (p.firstMessage() != null) f.add("firstMessage");
        return f;
    }

    static Set<String> shared(ProfileFacts a, ProfileFacts b) {
        Set<String> s = new HashSet<>();
        s.add(LOCATION);
        if (a.student() == b.student()) s.add("isStudent");
        if (a.student() && b.student() && a.schoolYear() != null && a.schoolYear().equals(b.schoolYear())) s.add("schoolYear");
        if (!Collections.disjoint(a.hobbies(), b.hobbies())) s.add("hobbies");
        a.hobbyOptions().forEach((q, v) -> {
            Set<String> theirs = b.hobbyOptions().get(q);
            if (theirs != null && !Collections.disjoint(v, theirs)) s.add(q);
        });
        if (!Collections.disjoint(a.relationshipLooking(), b.relationshipLooking())) s.add("relationshipLooking");
        if (a.matchingStyle() != null && a.matchingStyle().equals(b.matchingStyle())) s.add("matchingStyle");
        if (a.budget() != null && a.budget().equals(b.budget())) s.add("firstDateBudget");
        return s;
    }

    private static void addTerms(ProfileFacts p, List<String> terms) {
        terms.addAll(p.hobbyTexts().values());
        if (p.firstMessage() != null) terms.add(p.firstMessage());
        for (Lang lang : Lang.values()) {
            for (String h : p.hobbies()) add(terms, Labels.hobby(h, lang));
            p.hobbyOptions().forEach((q, opts) -> opts.forEach(o -> add(terms, Labels.of(q, o, lang))));
            p.relationshipLooking().forEach(o -> add(terms, Labels.of("relationshipLooking", o, lang)));
            add(terms, Labels.of("matchingStyle", p.matchingStyle(), lang));
            add(terms, Labels.of("schoolYear", p.schoolYear(), lang));
            add(terms, Labels.of("firstDateBudget", p.budget(), lang));
        }
    }

    private static void add(List<String> terms, String t) {
        if (t != null && !t.isBlank()) terms.add(t);
    }
}
