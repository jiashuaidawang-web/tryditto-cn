package cn.aiditto.domain.recall.v4;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline ablation. The selection rule and the holdout gate are fixed here, before a run is read.
 * A later stage replaces an earlier one only when dev Recall@20 is strictly higher.
 * R6 is dropped when its gain is below {@link #EXPLORE_MIN_GAIN}.
 */
public final class RecallAblation {
    public static final double HOLDOUT_RECALL_GATE = 0.60;
    public static final double EXPLORE_MIN_GAIN = 0.02;

    public record Stage(String name, Set<RecallRoute> routes, RecallMetrics.Summary dev) {}

    public record Report(List<Stage> stages, Stage selected, RecallMetrics.Summary holdout, Double ceilingRecall) {
        public boolean holdoutGatePassed() {
            return holdout.recallAt20() != null
                    && holdout.recallAt20() >= HOLDOUT_RECALL_GATE
                    && holdout.hardViolations() == 0
                    && holdout.expiredReturned() == 0
                    && holdout.withdrawnReturned() == 0
                    && holdout.safetyAuditViolations() == 0;
        }
    }

    private RecallAblation() {}

    public static Report run() {
        RecallBench bench = RecallBench.load();
        List<Stage> stages = new ArrayList<>();
        stages.add(measure("B0-random", Set.of(), bench));
        stages.add(measure("R1", V4RecallEngine.through(RecallRoute.R1), bench));
        stages.add(measure("R1-R2", V4RecallEngine.through(RecallRoute.R2), bench));
        stages.add(measure("R1-R3", V4RecallEngine.through(RecallRoute.R3), bench));
        stages.add(measure("R1-R4", V4RecallEngine.through(RecallRoute.R4), bench));
        stages.add(measure("R1-R5", V4RecallEngine.through(RecallRoute.R5), bench));
        stages.add(measure("R1-R6", V4RecallEngine.through(RecallRoute.R6), bench));
        Stage selected = select(stages);
        RecallMetrics.Summary holdout = evaluate(bench, "holdout", selected.routes(), RecallBench.LIST_SIZE);
        RecallMetrics.Summary ceiling = evaluate(bench, "dev", Set.of(), Integer.MAX_VALUE);
        return new Report(stages, selected, holdout, ceiling.recallReturned());
    }

    public static Stage select(List<Stage> stages) {
        Stage best = stages.get(0);
        for (Stage stage : stages) {
            if (stage.dev().recallAt20() != null && best.dev().recallAt20() != null
                    && stage.dev().recallAt20() > best.dev().recallAt20()) {
                best = stage;
            }
        }
        if ("R1-R6".equals(best.name())) {
            Stage previous = stages.stream().filter(stage -> "R1-R5".equals(stage.name())).findFirst().orElseThrow();
            if (best.dev().recallAt20() - previous.dev().recallAt20() < EXPLORE_MIN_GAIN) return previous;
        }
        return best;
    }

    private static Stage measure(String name, Set<RecallRoute> routes, RecallBench bench) {
        return new Stage(name, routes, evaluate(bench, "dev", routes, RecallBench.LIST_SIZE));
    }

    static RecallMetrics.Summary evaluate(RecallBench bench, String split, Set<RecallRoute> routes, int budget) {
        Map<Long, V4RecallEngine.Result> results = new LinkedHashMap<>();
        for (RecallSubject seeker : bench.split(split)) {
            results.put(seeker.userId(), V4RecallEngine.recall(
                    seeker, bench.subjects(), bench.input(), RecallBench.NOW, budget, routes, RecallBench.SEED));
        }
        return RecallMetrics.summarize(bench.split(split), results, bench.labels(split), bench.subjects(), bench.input());
    }
}
