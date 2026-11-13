package cn.aiditto.domain.matching;

/** Each hard constraint (技术方案 §7.2, PRD §5.2). Any violation removes the edge. */
public enum HardRule {
    /** Nobody is matched with themselves. */
    SELF,
    /** Both are 18+ on the drop day. Under-18s never enter the pool at all. */
    ADULT,
    /** Rule 1: same city pool. */
    SAME_POOL,
    /** Rule 2: each one's gender is in the other's {@code expectedGender}. */
    GENDER,
    /** Rule 3: each one's age is in the other's {@code expectedAgeRange}. */
    AGE,
    /** Rule 4: each one's height is in the other's {@code heightRange} unless "any". */
    HEIGHT,
    /** Rule 5: both can reach the midpoint within their travel minutes. */
    DISTANCE,
    /** Rule 6: never matched before, in any week, whatever the outcome. */
    NEVER_MATCHED,
    /** Rule 7: no report in either direction. */
    REPORTED,
    /** Rule 8: neither one's block list hashes hit the other's phone or email hashes. */
    BLOCKLIST,
    /** Rule 9: "super important" education means the partner's level is at least one's own. */
    EDUCATION,
    /** PRD §5.2: two students' school years are within the grade window. */
    GRADE_WINDOW,
    /** Rule 10: structured dealbreaker constraints (only when AI extraction succeeded). */
    AI_CONSTRAINTS,
    /** Rule 11: in manual runs, nobody with an unfinished match. */
    OPEN_MATCH
}
