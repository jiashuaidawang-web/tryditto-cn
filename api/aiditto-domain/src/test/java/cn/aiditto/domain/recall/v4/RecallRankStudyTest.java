package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilityRecallStudy;
import cn.aiditto.domain.matching.PairKey;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecallRankStudyTest {
    @Test
    void historicalRecallSplitStaysFrozen() {
        CompatibilityRecallStudy.Report report = CompatibilityRecallStudy.run();
        assertThat(report.recalledAt20()).isZero();
        assertThat(report.recalledAt50()).isEqualTo(4);
        assertThat(report.scoredInFullPool()).isEqualTo(8);
        assertThat(report.note()).contains("7/45");
    }

    @Test
    void coarseRankSeparatesRecallLossFromScoreLoss() {
        RecallRankFixture.Cohort dev = RecallRankFixture.dev();
        RecallRankFixture.Cohort holdout = RecallRankFixture.holdout();
        RecallRankStudy.Run narrow = RecallRankStudy.run(dev, RecallRankStudy.Scheme.A_RRF, RecallRankStudy.NARROW);
        RecallRankStudy.Run wide = RecallRankStudy.run(dev, RecallRankStudy.Scheme.F_PREFILTER, RecallRankStudy.WIDE);
        RecallRankStudy.Run holdoutNarrow = RecallRankStudy.run(holdout, RecallRankStudy.Scheme.A_RRF, RecallRankStudy.NARROW);
        RecallRankStudy.Run holdoutWide = RecallRankStudy.run(holdout, RecallRankStudy.Scheme.F_PREFILTER, RecallRankStudy.WIDE);
        System.out.println(RecallRankStudy.line(narrow));
        System.out.println(RecallRankStudy.line(wide));
        System.out.println(RecallRankStudy.line(holdoutNarrow));
        System.out.println(RecallRankStudy.line(holdoutWide));
        for (RecallRankStudy.Scheme scheme : RecallRankStudy.Scheme.values()) {
            if (scheme == RecallRankStudy.Scheme.A_RRF || scheme == RecallRankStudy.Scheme.F_PREFILTER) continue;
            System.out.println(RecallRankStudy.line(RecallRankStudy.run(dev, scheme, RecallRankStudy.WIDE)));
            System.out.println(RecallRankStudy.line(RecallRankStudy.run(holdout, scheme, RecallRankStudy.WIDE)));
        }
        assertThat(narrow.safetyViolations()).isZero();
        assertThat(wide.safetyViolations()).isZero();
        assertThat(holdoutNarrow.safetyViolations()).isZero();
        assertThat(holdoutWide.safetyViolations()).isZero();
        assertThat(wide.strongCovered()).isEqualTo(wide.strong());
        assertThat(holdoutWide.strongCovered()).isEqualTo(holdoutWide.strong());
        assertThat(narrow.strongCovered()).isLessThan(wide.strongCovered());
        assertThat(holdoutNarrow.strongCovered()).isLessThan(holdoutWide.strongCovered());
        long empty = wide.placements().stream().filter(item -> item.miss() == RecallRankStudy.Miss.NO_ROUTE_SIGNAL).count();
        assertThat(empty).isZero();
        long hiddenFromNarrow = narrow.placements().stream().filter(item -> item.miss() == RecallRankStudy.Miss.NO_ROUTE_SIGNAL).count();
        assertThat(hiddenFromNarrow).isPositive();
        for (RecallRankStudy.Budgets budgets : RecallRankStudy.SWEEP) {
            for (RecallRankStudy.Scheme scheme : List.of(
                    RecallRankStudy.Scheme.A_RRF, RecallRankStudy.Scheme.D_COVERAGE,
                    RecallRankStudy.Scheme.E_FULL_POOL, RecallRankStudy.Scheme.F_PREFILTER)) {
                RecallRankStudy.Run devSweep = RecallRankStudy.run(dev, scheme, budgets);
                RecallRankStudy.Run holdoutSweep = RecallRankStudy.run(holdout, scheme, budgets);
                System.out.println(RecallRankStudy.line(devSweep));
                System.out.println(RecallRankStudy.line(holdoutSweep));
                assertThat(devSweep.safetyViolations()).isZero();
                assertThat(holdoutSweep.safetyViolations()).isZero();
                assertThat(devSweep.quality().authHits()).isZero();
                assertThat(devSweep.quality().staleHits()).isZero();
                assertThat(devSweep.quality().hardHits()).isZero();
            }
        }
    }

    @Test
    void candidateGraphKeepsOneEdgePerPair() {
        List<RecallRankStudy.Edge> edges = RecallRankStudy.graph(RecallRankFixture.dev());
        Set<PairKey> keys = new HashSet<>();
        for (RecallRankStudy.Edge edge : edges) {
            assertThat(edge.lowId()).isLessThan(edge.highId());
            assertThat(keys.add(PairKey.of(edge.lowId(), edge.highId()))).isTrue();
            assertThat(edge.policy()).isEqualTo(RecallRankFixture.POLICY);
        }
        assertThat(edges).hasSize(RecallRankFixture.dev().people().size() - 1);
        int eligible = 0;
        int legal = 0;
        for (RecallRankStudy.Edge edge : edges) {
            if (edge.recommendationEligible()) eligible++;
            if ("LEGAL".equals(edge.legality())) legal++;
        }
        System.out.println("graph edges=" + edges.size() + " legal=" + legal + " eligible=" + eligible);
        for (RecallRankStudy.Edge edge : edges) {
            if (edge.recommendationEligible() || "HARD_AGE".equals(edge.legality()) || "PROFILE_WITHDRAWN".equals(edge.legality())) {
                System.out.println("edge " + edge.lowId() + "-" + edge.highId()
                        + " routes=" + edge.routes() + " recall=" + edge.recallRank()
                        + " coarse=" + edge.coarseRank() + " final=" + edge.finalRank()
                        + " ab=" + edge.scoreLowToHigh() + " ba=" + edge.scoreHighToLow()
                        + " cov=" + edge.coverageLowToHigh() + "/" + edge.coverageHighToLow()
                        + " eligible=" + edge.recommendationEligible()
                        + " legality=" + edge.legality() + " miss=" + edge.miss());
            }
        }
    }

    @Test
    void tiePoliciesStayReproducible() {
        List<RecallRankStudy.TieReport> reports = RecallRankStudy.ties(RecallRankFixture.dev());
        for (RecallRankStudy.TieReport report : reports) {
            System.out.println("tie=" + report.policy() + " exposed=" + report.uniqueExposed()
                    + " max=" + report.maxAppearances() + " truncated=" + report.strongTruncated()
                    + " sameFeature=" + report.sameFeatureExposed()
                    + " key=" + report.exposureKey()
                    + " reproducible=" + report.reproducible());
            assertThat(report.reproducible()).isTrue();
        }
        RecallRankStudy.TieReport baseline = reports.get(0);
        RecallRankStudy.TieReport seeded = reports.get(1);
        RecallRankStudy.TieReport week = reports.get(2);
        RecallRankStudy.TieReport mutual = reports.get(3);
        RecallRankStudy.TieReport explore = reports.get(4);
        assertThat(seeded.uniqueExposed()).isEqualTo(baseline.uniqueExposed());
        assertThat(seeded.maxAppearances()).isEqualTo(4);
        assertThat(seeded.exposureKey()).isNotEqualTo(baseline.exposureKey());
        assertThat(week.uniqueExposed()).isGreaterThan(baseline.uniqueExposed());
        assertThat(week.sameFeatureExposed()).isGreaterThan(baseline.sameFeatureExposed());
        assertThat(mutual.strongTruncated()).isLessThan(baseline.strongTruncated());
        assertThat(explore.uniqueExposed()).isGreaterThan(baseline.uniqueExposed());
    }

    @Test
    void scaleStopsAtTheFrozenClock() {
        List<RecallRankStudy.ScalePoint> points = RecallRankStudy.scale();
        assertThat(points).isNotEmpty();
        for (RecallRankStudy.ScalePoint point : points) {
            System.out.println("scale users=" + point.users() + " edges=" + point.legalEdges()
                    + " fullMs=" + point.fullPoolMs() + " recallMs=" + point.engineRecallMs()
                    + " p50us=" + point.scoreP50Us() + " p95us=" + point.scoreP95Us()
                    + " cpuMs=" + point.cpuMs() + " heap=" + point.heapSampleBytes()
                    + " data=" + point.dataRead() + " vector=" + point.vector() + " " + point.note());
            assertThat(point.vector()).isEqualTo("NOT_RUN");
            if (point.fullPoolMs() != null) assertThat(point.fullPoolMs()).isLessThan(RecallRankStudy.SCALE_WALL_MS);
        }
    }
}
