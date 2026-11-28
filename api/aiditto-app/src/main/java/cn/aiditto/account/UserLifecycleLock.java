package cn.aiditto.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One lock order for account deletion, learning, feedback, and group observations.
 *
 * <ol>
 *   <li>User row locks, ascending user id, via {@link UserLock}.</li>
 *   <li>Feedback advisory locks, class 11, same id order.</li>
 *   <li>Match advisory lock, class 12, when the work names one match.</li>
 * </ol>
 *
 * A missing user has no row, so only the advisory lock is taken. Callers that already hold
 * the user row lock may call this again in the same transaction. PostgreSQL treats both
 * the row lock and the advisory lock as reentrant there. This class does not set a lock timeout.
 */
@Component
public class UserLifecycleLock {
    public static final int FEEDBACK = 11;
    public static final int MATCH = 12;

    private final UserLock users;
    private final JdbcTemplate jdbc;

    public UserLifecycleLock(UserLock users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    public void userThenFeedback(long userId) {
        users.lock(userId);
        feedback(userId);
    }

    public void pairThenMatch(long userA, long userB, long matchId) {
        long first = Math.min(userA, userB);
        long second = Math.max(userA, userB);
        users.lock(first);
        if (second != first) users.lock(second);
        feedback(first);
        if (second != first) feedback(second);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(12, ?::int)", (int) matchId);
    }

    private void feedback(long userId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(11, ?::int)", (int) userId);
    }
}
