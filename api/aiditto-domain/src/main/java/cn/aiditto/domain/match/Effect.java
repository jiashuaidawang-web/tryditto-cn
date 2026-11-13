package cn.aiditto.domain.match;

/**
 * Side effects outside the match/side/feedback rows that the service must carry out in the same
 * transaction. Row changes themselves are expressed by the new {@link MatchSnapshot}.
 */
public sealed interface Effect {

    /** Didn't respond by Thursday 22:00 (counts toward the 2-weeks auto pause, PRD §6.6). */
    record RecordNoResponse(long userId) implements Effect {}

    /** Cancelled within 6 hours of the start; two count as one no-show. */
    record RecordLateCancel(long userId) implements Effect {}

    /** Increment {@code user.no_show_count} and apply {@link Penalties#forNoShows}. */
    record RecordNoShow(long userId) implements Effect {}

    /** Contradicting feedback; 3 disputes for the same person go to manual review. */
    record RecordDispute(long userA, long userB) implements Effect {}

    /** Both want to meet again: WeChat ids become visible to each other. */
    record UnlockContacts(long userA, long userB) implements Effect {}

    /** Insert a {@code report} row. The pair can never be matched again. */
    record CreateReport(long reporterId, long reportedId, ReportCategory category, boolean fromFeedback)
            implements Effect {
        public boolean urgent() {
            return category.urgent();
        }
    }

    /** Urgent report: take the reported user out of matching until an admin reviews it. */
    record SuspendPendingReview(long userId) implements Effect {}
}
