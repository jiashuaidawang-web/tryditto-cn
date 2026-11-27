package cn.aiditto.domain.feedback.v4;

import java.util.ArrayList;
import java.util.List;

/**
 * Counts over finished observations. An immature match is not a failure.
 * Published-match rates and responded-match rates are separate ratios.
 */
public final class FeedbackMetrics {
    private FeedbackMetrics() {}

    public record Ratio(String name, int numerator, int denominator, String window, String dedupe) {
        public String fraction() {
            return numerator + "/" + denominator;
        }
    }

    public record Report(List<Ratio> publishedBase, List<Ratio> respondedBase, int immature, int contested, int retracted, int eligible) {
        public Report {
            publishedBase = List.copyOf(publishedBase);
            respondedBase = List.copyOf(respondedBase);
        }
    }

    public static Report summarize(List<FeedbackProjection> projections, List<Boolean> mature) {
        if (projections.size() != mature.size()) throw new FeedbackRejection("WINDOW");
        int published = 0;
        int responded = 0;
        int viewed = 0;
        int oneAccept = 0;
        int bothAccept = 0;
        int declined = 0;
        int expired = 0;
        int scheduled = 0;
        int oneAttended = 0;
        int bothAttended = 0;
        int oneContinue = 0;
        int bothContinue = 0;
        int covered = 0;
        int contested = 0;
        int retracted = 0;
        int revoked = 0;
        int eligible = 0;
        int immature = 0;
        int respondedOneAccept = 0;
        int respondedBothAccept = 0;
        for (int i = 0; i < projections.size(); i++) {
            FeedbackProjection projection = projections.get(i);
            if (!projection.published()) continue;
            if (!mature.get(i)) {
                immature++;
                continue;
            }
            published++;
            if (projection.learningAllowed()) eligible++;
            if (projection.contested()) contested++;
            if (projection.retracted()) retracted++;
            if (projection.processingRevoked()) revoked++;
            boolean sawA = projection.viewedA();
            boolean sawB = projection.viewedB();
            if (sawA || sawB) viewed++;
            boolean acceptA = projection.responseA() == FeedbackFacts.ResponseState.ACCEPTED;
            boolean acceptB = projection.responseB() == FeedbackFacts.ResponseState.ACCEPTED;
            boolean declineA = projection.responseA() == FeedbackFacts.ResponseState.DECLINED;
            boolean declineB = projection.responseB() == FeedbackFacts.ResponseState.DECLINED;
            boolean expiredA = projection.responseA() == FeedbackFacts.ResponseState.EXPIRED;
            boolean expiredB = projection.responseB() == FeedbackFacts.ResponseState.EXPIRED;
            if (acceptA || acceptB) oneAccept++;
            if (projection.mutualAcceptance()) bothAccept++;
            if (declineA || declineB) declined++;
            if ((expiredA || expiredB) && !declineA && !declineB) expired++;
            if (projection.schedule() == FeedbackFacts.ScheduleState.SCHEDULED
                    || projection.schedule() == FeedbackFacts.ScheduleState.CANCELLED) scheduled++;
            if (projection.attendance() == FeedbackFacts.AttendanceState.ONE_REPORTED_ATTENDED
                    || projection.attendance() == FeedbackFacts.AttendanceState.BOTH_REPORTED_ATTENDED) oneAttended++;
            if (projection.attendance() == FeedbackFacts.AttendanceState.BOTH_REPORTED_ATTENDED) bothAttended++;
            if (projection.continueA() == FeedbackFacts.ContinueState.EXPRESSED
                    || projection.continueB() == FeedbackFacts.ContinueState.EXPRESSED) oneContinue++;
            if (projection.bothContinue()) bothContinue++;
            boolean respondedMatch = acceptA || acceptB || declineA || declineB;
            if (respondedMatch) {
                responded++;
                covered++;
                if (acceptA || acceptB) respondedOneAccept++;
                if (projection.mutualAcceptance()) respondedBothAccept++;
            }
        }
        String window = "mature published matches in the supplied window";
        String dedupe = "one match, not one event";
        List<Ratio> publishedBase = List.of(
                ratio("view_rate", viewed, published, window, dedupe),
                ratio("one_accept_rate", oneAccept, published, window, dedupe),
                ratio("mutual_accept_rate", bothAccept, published, window, dedupe),
                ratio("decline_rate", declined, published, window, dedupe),
                ratio("expiry_rate", expired, published, window, dedupe),
                ratio("schedule_rate", scheduled, published, window, dedupe),
                ratio("reported_attendance_rate", oneAttended, published, window, dedupe),
                ratio("mutual_attendance_rate", bothAttended, published, window, dedupe),
                ratio("one_continue_rate", oneContinue, published, window, dedupe),
                ratio("mutual_continue_rate", bothContinue, published, window, dedupe),
                ratio("coverage_rate", covered, published, window, dedupe),
                ratio("contest_rate", contested, published, window, dedupe),
                ratio("retract_rate", retracted, published, window, dedupe),
                ratio("processing_revoked_rate", revoked, published, window, dedupe),
                ratio("learning_eligible_rate", eligible, published, window, dedupe));
        List<Ratio> respondedBase = new ArrayList<>();
        respondedBase.add(ratio("one_accept_rate_among_responded", respondedOneAccept, responded, "mature matches with a response", dedupe));
        respondedBase.add(ratio("mutual_accept_rate_among_responded", respondedBothAccept, responded, "mature matches with a response", dedupe));
        return new Report(publishedBase, respondedBase, immature, contested, retracted, eligible);
    }

    private static Ratio ratio(String name, int numerator, int denominator, String window, String dedupe) {
        return new Ratio(name, numerator, denominator, window, dedupe);
    }
}
