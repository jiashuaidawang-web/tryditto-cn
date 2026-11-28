package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.strategy.RelationshipLabels.Mutual;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline check that a score can be turned into a synthetic frequency.
 * The number is not a probability for a real user, and it is not written back to the scorer.
 */
public final class ScoreCalibration {
    public static final int ISOTONIC_MINIMUM = 40;
    public static final String REAL_QUALITY = "NOT_RUN";

    private ScoreCalibration() {}

    public enum Target { SCORE_AB_ACCEPT_A, SCORE_BA_ACCEPT_B, RECIPROCAL_MUTUAL, ATTENDANCE, CONTINUE }
    public enum Method { UNCALIBRATED, CONSTANT_RATE, BINNING, LOGISTIC, ISOTONIC }

    public record Result(Target target, Method method, String status, int sample, Double brier, Double logLoss,
                         Double calibrationError) {}

    static double[] probabilities(Method method, Target target, List<MatchingObservation> fitRows, List<MatchingObservation> evalRows) {
        return switch (method) {
            case UNCALIBRATED -> examples(evalRows, target).stream().mapToDouble(example -> clamp(example.score / 100.0)).toArray();
            case CONSTANT_RATE -> constantRate(examples(fitRows, target), examples(evalRows, target).size());
            case BINNING -> applyBins(examples(fitRows, target), examples(evalRows, target));
            case LOGISTIC -> applyLogistic(examples(fitRows, target), examples(evalRows, target));
            case ISOTONIC -> applyIsotonic(examples(fitRows, target), examples(evalRows, target));
        };
    }

    /** Fits and scores the same rows. Boundary tests use this. It is not a holdout result. */
    public static List<Result> run(List<MatchingObservation> rows) {
        return evaluate(rows, rows);
    }

    /** Fits on the first list and scores the second. Holdout labels do not set the fitted rates. */
    public static List<Result> evaluate(List<MatchingObservation> fitRows, List<MatchingObservation> evalRows) {
        List<Result> results = new ArrayList<>();
        for (Target target : Target.values()) {
            List<Example> fit = examples(fitRows, target);
            List<Example> eval = examples(evalRows, target);
            for (Method method : Method.values()) {
                results.add(score(target, method, fit, eval));
            }
        }
        return List.copyOf(results);
    }

    private static Result score(Target target, Method method, List<Example> fit, List<Example> eval) {
        if (eval.size() < 2 || single(eval)) {
            return new Result(target, method, "INSUFFICIENT", eval.size(), null, null, null);
        }
        if (method == Method.ISOTONIC && fit.size() < ISOTONIC_MINIMUM) {
            return new Result(target, method, "NOT_RUN", eval.size(), null, null, null);
        }
        if (method != Method.UNCALIBRATED && (fit.size() < 2 || single(fit))) {
            return new Result(target, method, "INSUFFICIENT", eval.size(), null, null, null);
        }
        double[] probability = switch (method) {
            case UNCALIBRATED -> eval.stream().mapToDouble(example -> clamp(example.score / 100.0)).toArray();
            case CONSTANT_RATE -> constantRate(fit, eval.size());
            case BINNING -> applyBins(fit, eval);
            case LOGISTIC -> applyLogistic(fit, eval);
            case ISOTONIC -> applyIsotonic(fit, eval);
        };
        for (int i = 0; i < probability.length; i++) {
            if (!Double.isFinite(probability[i])) probability[i] = 0.5;
            probability[i] = clamp(probability[i]);
        }
        return new Result(target, method, "OK", eval.size(), brier(eval, probability),
                logLoss(eval, probability), error(eval, probability));
    }

    private static boolean single(List<Example> examples) {
        return examples.stream().allMatch(example -> example.label == examples.get(0).label);
    }

    private static double[] applyBins(List<Example> fit, List<Example> eval) {
        double[] sum = new double[5];
        int[] count = new int[5];
        for (Example example : fit) {
            int bin = Math.min(4, example.score / 20);
            sum[bin] += example.label;
            count[bin] += 1;
        }
        double[] probability = new double[eval.size()];
        for (int i = 0; i < eval.size(); i++) {
            int bin = Math.min(4, eval.get(i).score / 20);
            probability[i] = count[bin] == 0 ? clamp(eval.get(i).score / 100.0) : clamp(sum[bin] / count[bin]);
        }
        return probability;
    }

    private static double[] applyLogistic(List<Example> fit, List<Example> eval) {
        double intercept = 0;
        double slope = 0;
        for (int step = 0; step < 25; step++) {
            double g0 = 0, g1 = 0, h00 = 0, h01 = 0, h11 = 0;
            for (Example example : fit) {
                double p = sigmoid(intercept + slope * example.score / 100.0);
                double weight = Math.max(1e-6, p * (1 - p));
                g0 += example.label - p;
                g1 += (example.label - p) * example.score / 100.0;
                h00 += weight;
                h01 += weight * example.score / 100.0;
                h11 += weight * example.score / 100.0 * example.score / 100.0;
            }
            double det = h00 * h11 - h01 * h01;
            if (Math.abs(det) < 1e-12) break;
            intercept += (h11 * g0 - h01 * g1) / det;
            slope += (h00 * g1 - h01 * g0) / det;
        }
        double[] probability = new double[eval.size()];
        for (int i = 0; i < eval.size(); i++) {
            probability[i] = clamp(sigmoid(intercept + slope * eval.get(i).score / 100.0));
        }
        return probability;
    }

    private static double[] constantRate(List<Example> fit, int size) {
        double rate = 0;
        for (Example example : fit) rate += example.label;
        double probability = clamp(rate / fit.size());
        double[] out = new double[size];
        java.util.Arrays.fill(out, probability);
        return out;
    }

    private static double[] applyIsotonic(List<Example> fit, List<Example> eval) {
        java.util.Map<Integer, double[]> grouped = new java.util.TreeMap<>();
        for (Example example : fit) {
            double[] cell = grouped.computeIfAbsent(example.score, score -> new double[2]);
            cell[0] += example.label;
            cell[1] += 1;
        }
        List<double[]> blocks = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            blocks.add(new double[] {entry.getValue()[0], entry.getValue()[1], entry.getKey()});
            while (blocks.size() >= 2) {
                double[] previous = blocks.get(blocks.size() - 2);
                double[] current = blocks.get(blocks.size() - 1);
                if (previous[0] / previous[1] <= current[0] / current[1]) break;
                previous[0] += current[0];
                previous[1] += current[1];
                blocks.remove(blocks.size() - 1);
            }
        }
        double[] byScore = new double[101];
        java.util.Arrays.fill(byScore, Double.NaN);
        for (double[] block : blocks) {
            double value = clamp(block[0] / block[1]);
            int start = (int) block[2];
            byScore[Math.min(100, start)] = value;
        }
        double carry = Double.NaN;
        for (int score = 0; score <= 100; score++) {
            if (!Double.isNaN(byScore[score])) carry = byScore[score];
            else byScore[score] = carry;
        }
        double[] probability = new double[eval.size()];
        for (int i = 0; i < eval.size(); i++) {
            double value = byScore[eval.get(i).score];
            probability[i] = Double.isNaN(value) ? clamp(eval.get(i).score / 100.0) : value;
        }
        return probability;
    }

    private static List<Example> examples(List<MatchingObservation> rows, Target target) {
        List<Example> examples = new ArrayList<>();
        for (MatchingObservation row : rows) {
            RelationshipLabels labels = RelationshipLabels.of(row);
            Integer label = switch (target) {
                case SCORE_AB_ACCEPT_A -> binary(row.responseA() == MatchingObservation.Response.ACCEPTED,
                        row.responseA() == MatchingObservation.Response.DECLINED, labels.y1() != RelationshipLabels.Mutual.WITHHELD);
                case SCORE_BA_ACCEPT_B -> binary(row.responseB() == MatchingObservation.Response.ACCEPTED,
                        row.responseB() == MatchingObservation.Response.DECLINED, labels.y1() != RelationshipLabels.Mutual.WITHHELD);
                case RECIPROCAL_MUTUAL -> mutualLabel(labels);
                case ATTENDANCE -> attendanceLabel(labels);
                case CONTINUE -> continueLabel(labels);
            };
            if (label == null) continue;
            int score = switch (target) {
                case SCORE_AB_ACCEPT_A -> row.scoreAB();
                case SCORE_BA_ACCEPT_B -> row.scoreBA();
                case RECIPROCAL_MUTUAL -> row.reciprocalScore();
                case ATTENDANCE, CONTINUE -> row.reciprocalScore();
            };
            examples.add(new Example(score, label));
        }
        return examples;
    }

    private static Integer mutualLabel(RelationshipLabels labels) {
        if (!labels.y1Binary()) return null;
        return labels.y1() == Mutual.BOTH_ACCEPTED ? 1 : 0;
    }

    private static Integer attendanceLabel(RelationshipLabels labels) {
        if (labels.y2() == RelationshipLabels.AttendanceLabel.BOTH_ATTENDED) return 1;
        if (labels.y2() == RelationshipLabels.AttendanceLabel.BOTH_ABSENT) return 0;
        return null;
    }

    private static Integer continueLabel(RelationshipLabels labels) {
        if (labels.y3() == RelationshipLabels.ContinueLabel.BOTH_EXPRESSED) return 1;
        if (labels.y3() == RelationshipLabels.ContinueLabel.BOTH_DECLINED) return 0;
        return null;
    }

    private static Integer binary(boolean positive, boolean negative, boolean allowed) {
        if (!allowed || positive == negative) return null;
        return positive ? 1 : 0;
    }

    private static double brier(List<Example> examples, double[] probability) {
        double sum = 0;
        for (int i = 0; i < examples.size(); i++) {
            double gap = probability[i] - examples.get(i).label;
            sum += gap * gap;
        }
        return sum / examples.size();
    }

    private static double logLoss(List<Example> examples, double[] probability) {
        double sum = 0;
        for (int i = 0; i < examples.size(); i++) {
            double p = probability[i];
            sum += examples.get(i).label == 1 ? -Math.log(p) : -Math.log(1 - p);
        }
        return sum / examples.size();
    }

    private static double error(List<Example> examples, double[] probability) {
        double[] sum = new double[5];
        double[] predicted = new double[5];
        int[] count = new int[5];
        for (int i = 0; i < examples.size(); i++) {
            int bin = Math.min(4, (int) (probability[i] * 5));
            if (bin == 5) bin = 4;
            sum[bin] += examples.get(i).label;
            predicted[bin] += probability[i];
            count[bin] += 1;
        }
        double error = 0;
        for (int bin = 0; bin < 5; bin++) {
            if (count[bin] == 0) continue;
            error += count[bin] * Math.abs(sum[bin] / count[bin] - predicted[bin] / count[bin]);
        }
        return error / examples.size();
    }

    private static double sigmoid(double value) {
        if (value > 20) return 1 - 1e-6;
        if (value < -20) return 1e-6;
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private static double clamp(double value) {
        if (value < 1e-6) return 1e-6;
        if (value > 1 - 1e-6) return 1 - 1e-6;
        return value;
    }

    private record Example(int score, int label) {}
}
