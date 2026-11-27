package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.matching.HardFilter;
import cn.aiditto.domain.matching.MatchingInput;

import java.util.Collection;
import java.util.Map;

/** Re-checks finished candidate lists. It does not trust the counter stored on the recall result. */
public final class RecallSafetyAudit {
    private RecallSafetyAudit() {}

    public static int violations(Map<Long, RecallSubject> pool, MatchingInput input, Collection<V4RecallEngine.Result> results) {
        int found = 0;
        for (V4RecallEngine.Result result : results) {
            RecallSubject seeker = pool.get(result.hits().isEmpty() ? null : result.hits().get(0).userId());
            for (V4RecallEngine.Hit hit : result.hits()) {
                if (seeker == null) seeker = pool.get(hit.userId());
                RecallSubject other = pool.get(hit.candidateId());
                if (seeker == null || other == null || !other.current() || other.withdrawn()
                        || !HardFilter.passes(seeker.candidate(), other.candidate(), input)) {
                    found++;
                }
            }
        }
        return found;
    }
}
