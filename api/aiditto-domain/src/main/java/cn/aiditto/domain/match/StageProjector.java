package cn.aiditto.domain.match;

import cn.aiditto.domain.slot.Deadlines;
import java.time.Instant;
import java.util.Objects;

/**
 * Computes the viewer's {@link Stage} from stored facts and {@code now}; nothing here is persisted
 * (技术方案 §5.2). Rows are checked in order and the first match wins. The function is total: every
 * combination of inputs yields exactly one stage, unreachable ones fall through to {@link Stage#CLOSED}.
 */
public final class StageProjector {

    /**
     * @param match this week's match for the viewer, or {@code null} when they have none
     * @param weekPublished whether this week's batch has been published
     */
    public record Input(long viewerId, MatchSnapshot match, boolean weekPublished,
                        ParticipationStatus participation, Instant now) {
        public Input {
            Objects.requireNonNull(participation, "participation");
            Objects.requireNonNull(now, "now");
        }
    }

    private StageProjector() {}

    public static Stage project(Input in) {
        MatchSnapshot m = in.match();
        if (m == null || m.state() == MatchState.DRAFT) {
            if (in.participation() == ParticipationStatus.SUSPENDED) return Stage.SUSPENDED;
            if (in.participation() == ParticipationStatus.PAUSED) return Stage.PAUSED;
            return in.weekPublished() && m == null ? Stage.EMPTY_WEEK : Stage.WAITING;
        }
        long me = in.viewerId();
        SideSnapshot mine = m.side(me);
        SideSnapshot theirs = m.partnerOf(me);
        Instant now = in.now();

        if (m.reporterId() != null) {
            return m.reporterId() == me ? Stage.REPORTED : Stage.CLOSED;
        }
        return switch (m.state()) {
            case PUBLISHED -> switch (mine.response()) {
                case PENDING -> Stage.REVEALED;
                case DECLINE -> Stage.DECLINED;
                case ACCEPT -> theirs.response() == Response.PENDING ? Stage.AWAITING_PARTNER : Stage.CLOSED;
            };
            case DECLINED -> mine.response() == Response.DECLINE ? Stage.DECLINED : Stage.CLOSED;
            case EXPIRED, UNSCHEDULED, CANCELLED, HALTED -> Stage.CLOSED;
            case NO_OVERLAP -> Stage.NO_OVERLAP;
            case CONFIRMED -> {
                if (now.isBefore(Deadlines.dateDayOpens(m.slotStart()))) yield Stage.CONFIRMED;
                yield mine.checkedInAt() != null ? Stage.CHECKED_IN : Stage.DATE_DAY;
            }
            case FEEDBACK_OPEN -> mine.feedback() == null ? Stage.FEEDBACK : Stage.AWAITING_FEEDBACK;
            case CLOSED -> m.outcome() == Outcome.MUTUAL ? Stage.MUTUAL : Stage.CLOSED;
            case DRAFT -> Stage.WAITING;
        };
    }

    /** WeChat ids are only ever returned in the {@link Stage#MUTUAL} stage (F11, PRD §4.7). */
    public static boolean contactVisible(Stage stage) {
        return stage == Stage.MUTUAL;
    }
}
