package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores extraction against labels written with the samples.
 * Precision and recall use model-origin keys only. Questionnaire facts are not counted as model hits.
 */
public final class ProfileEval {
    private ProfileEval() {}

    public record Sample(
            String id,
            String split,
            String category,
            ProfileContext context,
            Map<String, Object> answers,
            List<ExtractedFeature> modelOutput,
            boolean parsed,
            Set<String> expectedModelKeys,
            List<String> sensitiveNeedles) {

        public Sample {
            answers = Map.copyOf(answers);
            modelOutput = modelOutput == null ? List.of() : List.copyOf(modelOutput);
            expectedModelKeys = Set.copyOf(expectedModelKeys);
            sensitiveNeedles = List.copyOf(sensitiveNeedles);
        }
    }

    public record Metrics(
            String split,
            int samples,
            int truePositives,
            int falsePositives,
            int falseNegatives,
            double precision,
            double recall,
            int acceptedModelFeatures,
            int sectionConfusions,
            double confusionRate,
            int rawFeatures,
            int ungroundedRaw,
            double ungroundedRate,
            int invalidRefRaw,
            double invalidRefRate,
            int ungroundedAccepted,
            int invalidRefAccepted,
            int coldStartSamples,
            int coldStartKeptUnknown,
            double unknownRetention,
            int hardRequests,
            int hardAccepted,
            int sensitiveSamples,
            int sensitiveLeaks,
            double sensitiveLeakRate,
            int structuredAttempts,
            int structuredSuccesses,
            double structuredSuccessRate,
            int badFormatSamples,
            int badFormatParsed,
            double badFormatSuccessRate,
            long scoringNanos) {}

    public record Report(int samples, Metrics dev, Metrics holdout) {}

    public static Report score(List<Sample> samples, FeatureRegistry registry) {
        return new Report(samples.size(), score(samples, "dev", registry), score(samples, "holdout", registry));
    }

    public static Metrics score(List<Sample> samples, String split, FeatureRegistry registry) {
        int samplesN = 0;
        int tp = 0;
        int fp = 0;
        int fn = 0;
        int acceptedN = 0;
        int confusions = 0;
        int raw = 0;
        int ungroundedRaw = 0;
        int invalidRaw = 0;
        int ungroundedAccepted = 0;
        int invalidAccepted = 0;
        int cold = 0;
        int coldKept = 0;
        int hardRequests = 0;
        int hardAccepted = 0;
        int sensitiveSamples = 0;
        int leaks = 0;
        int structuredAttempts = 0;
        int structuredSuccesses = 0;
        int badSamples = 0;
        int badParsed = 0;
        long started = System.nanoTime();
        for (Sample sample : samples) {
            if (!split.equals(sample.split())) continue;
            samplesN++;
            boolean badFormat = "bad-format".equals(sample.category());
            if (badFormat) {
                badSamples++;
                if (sample.parsed()) badParsed++;
                continue;
            }
            structuredAttempts++;
            if (sample.parsed()) structuredSuccesses++;
            if ("sensitive".equals(sample.category())) {
                sensitiveSamples++;
                boolean leaked = false;
                for (String needle : sample.sensitiveNeedles()) {
                    if (needle != null && sample.context().exportCanonical().contains(needle)) leaked = true;
                }
                if (leaked) leaks++;
            }
            List<ExtractedFeature> output = sample.parsed() ? sample.modelOutput() : List.of();
            raw += output.size();
            for (ExtractedFeature feature : output) {
                if (feature.hardConstraint()) hardRequests++;
                if (feature.evidenceRefs().isEmpty()) ungroundedRaw++;
                if (feature.evidenceRefs().stream().anyMatch(ref -> sample.context().item(ref) == null)) invalidRaw++;
            }
            ProfileExtractionValidator.Result result = ProfileExtractionValidator.check(sample.context(), output, registry);
            hardAccepted += (int) result.accepted().stream()
                    .filter(item -> item.constraintType() == ConstraintType.DOCUMENTED_EXISTING_RULE).count();
            List<ProfileEvidence> deterministic = RelationshipProfileProjector.project(
                    sample.context().userId(), sample.answers(), registry).evidence();
            RelationshipProfileSnapshot merged = ProfileMerger.merge(
                    sample.context().userId(), deterministic, result.accepted(), registry);
            Set<String> actual = modelKeys(merged);
            acceptedN += actual.size();
            for (String key : actual) {
                if (sample.expectedModelKeys().contains(key)) tp++;
                else fp++;
                if (confused(key, sample.expectedModelKeys())) confusions++;
            }
            for (String expected : sample.expectedModelKeys()) {
                if (!actual.contains(expected)) fn++;
            }
            ungroundedAccepted += result.accepted().stream().filter(item -> item.sourceRef() == null || item.sourceRef().isBlank()).count();
            invalidAccepted += result.accepted().stream().filter(item -> sample.context().item(item.sourceRef()) == null).count();
            if ("cold-start".equals(sample.category())) {
                cold++;
                if (actual.isEmpty() && sample.expectedModelKeys().isEmpty()) coldKept++;
            }
        }
        long scoring = System.nanoTime() - started;
        return new Metrics(split, samplesN, tp, fp, fn, ratio(tp, tp + fp), ratio(tp, tp + fn), acceptedN,
                confusions, acceptedN == 0 ? 0 : (double) confusions / acceptedN,
                raw, ungroundedRaw, raw == 0 ? 0 : (double) ungroundedRaw / raw,
                invalidRaw, raw == 0 ? 0 : (double) invalidRaw / raw,
                ungroundedAccepted, invalidAccepted, cold, coldKept, cold == 0 ? 1 : (double) coldKept / cold,
                hardRequests, hardAccepted, sensitiveSamples, leaks, sensitiveSamples == 0 ? 0 : (double) leaks / sensitiveSamples,
                structuredAttempts, structuredSuccesses, structuredAttempts == 0 ? 1 : (double) structuredSuccesses / structuredAttempts,
                badSamples, badParsed, badSamples == 0 ? 0 : (double) badParsed / badSamples, scoring);
    }

    private static boolean confused(String actual, Set<String> expected) {
        String[] parts = actual.split("\\|", 3);
        if (parts.length < 2) return false;
        for (String key : expected) {
            String[] other = key.split("\\|", 3);
            if (other.length >= 2 && other[1].equals(parts[1]) && !other[0].equals(parts[0])) return true;
        }
        return false;
    }

    private static Set<String> modelKeys(RelationshipProfileSnapshot snapshot) {
        Map<String, List<ProfileEvidence>> grouped = new LinkedHashMap<>();
        for (ProfileEvidence evidence : snapshot.evidence()) {
            if (evidence.sourceType() != EvidenceSource.MODEL_INFERENCE) continue;
            grouped.computeIfAbsent(evidence.section().name() + "|" + evidence.featureCode(), key -> new ArrayList<>()).add(evidence);
        }
        Set<String> keys = new LinkedHashSet<>();
        for (var entry : grouped.entrySet()) {
            boolean same = entry.getValue().stream().allMatch(item -> item.claim().equals(entry.getValue().get(0).claim()));
            if (!same) keys.add(entry.getKey() + "|CONTESTED");
            else keys.add(entry.getKey() + "|" + ProfileContextBuilder.valueToken(entry.getValue().get(0).claim()));
        }
        return keys;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }
}
