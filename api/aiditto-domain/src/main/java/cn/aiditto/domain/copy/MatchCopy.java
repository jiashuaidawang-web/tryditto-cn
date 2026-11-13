package cn.aiditto.domain.copy;

import java.util.List;

/**
 * Card copy for one match (技术方案 §4.6 {@code match_copy}, §8.1): one {@link ViewerCopy} per viewer plus
 * the chosen venue. Texts use {@code {{partner}}} / {{me}} placeholders and the pronoun "ta"; the app
 * substitutes names and pronouns when reading.
 */
public record MatchCopy(ViewerCopy forLow, ViewerCopy forHigh, String venueId, String activity, String extra,
                        Source source) {

    public enum Source { AI, TEMPLATE }

    /** What one viewer reads about their partner. */
    public record ViewerCopy(String headline, List<String> blurb, List<Reason> reasons, List<String> openers) {
        public ViewerCopy {
            blurb = blurb == null ? null : List.copyOf(blurb);
            reasons = reasons == null ? null : List.copyOf(reasons);
            openers = openers == null ? null : List.copyOf(openers);
        }
    }

    /** One "why you two" reason; {@code cites} are question ids from the input whitelist. */
    public record Reason(String text, String basis, List<String> cites) {
        public Reason {
            cites = cites == null ? null : List.copyOf(cites);
        }
    }
}
