package cn.aiditto.shadow;

import cn.aiditto.domain.compatibility.v4.BusinessFeatureCoverage;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.GenderPref;
import cn.aiditto.domain.relationship.EvidenceIds;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Canonical text for a shadow snapshot. Free text, contact data, and photos stay out. */
final class ShadowText {
    private ShadowText() {}

    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String document(ShadowRequest request, ProductMatchingPoolReader.PoolRead pool, String commit) {
        StringBuilder out = new StringBuilder(2048);
        out.append("{\"run\":").append(quote(request.matchingRunId()));
        out.append(",\"city\":").append(quote(request.city()));
        out.append(",\"cohort\":").append(quote(request.cohort()));
        out.append(",\"period\":").append(quote(request.matchingPeriod().toString()));
        out.append(",\"cutoff\":").append(quote(request.sourceCutoff().toString()));
        out.append(",\"strategy\":").append(quote(request.candidateStrategyVersion()));
        out.append(",\"policy\":").append(quote(request.compatibilityPolicyVersion()));
        out.append(",\"objective\":").append(quote(request.globalObjectiveVersion()));
        out.append(",\"commit\":").append(quote(commit));
        out.append(",\"eligible\":[");
        List<ProductMatchingPoolReader.Eligible> eligible = new ArrayList<>(pool.eligible());
        eligible.sort(Comparator.comparingLong(row -> row.member().userId()));
        for (int i = 0; i < eligible.size(); i++) {
            if (i > 0) out.append(',');
            person(out, eligible.get(i));
        }
        out.append("],\"excluded\":[");
        List<ProductMatchingPoolReader.Exclusion> excluded = new ArrayList<>(pool.excluded());
        excluded.sort(Comparator.comparingLong(ProductMatchingPoolReader.Exclusion::userId));
        for (int i = 0; i < excluded.size(); i++) {
            if (i > 0) out.append(',');
            ProductMatchingPoolReader.Exclusion row = excluded.get(i);
            out.append("{\"id\":").append(row.userId()).append(",\"reason\":").append(quote(row.reason().name())).append('}');
        }
        return out.append("]}").toString();
    }

    static String hash(String document) {
        return EvidenceIds.sha256(document);
    }

    static BusinessFeatureCoverage.Report coverage(ProductMatchingPoolReader.PoolRead pool) {
        int targets = 0;
        int scoreable = 0;
        int noTarget = 0;
        int unscored = 0;
        int unknown = 0;
        for (ProductMatchingPoolReader.Eligible row : pool.eligible()) {
            BusinessFeatureCoverage.Report one = BusinessFeatureCoverage.of(row.snapshot());
            targets = one.alignmentTargets();
            scoreable += one.scoreableAlignments();
            noTarget += one.noAlignmentTarget();
            unscored += one.legalUnscored();
            unknown += one.unknown();
        }
        return new BusinessFeatureCoverage.Report(targets, scoreable, noTarget, unscored, unknown);
    }

    static String coverageJson(BusinessFeatureCoverage.Report report, int recommended, int legal) {
        return "{\"alignmentTargets\":" + report.alignmentTargets()
                + ",\"scoreableAlignments\":" + report.scoreableAlignments()
                + ",\"noAlignmentTarget\":" + report.noAlignmentTarget()
                + ",\"legalUnscored\":" + report.legalUnscored()
                + ",\"unknown\":" + report.unknown()
                + ",\"recommendedEdges\":" + recommended
                + ",\"legalEdges\":" + legal + "}";
    }

    private static void person(StringBuilder out, ProductMatchingPoolReader.Eligible row) {
        Candidate candidate = row.member().candidate();
        out.append("{\"id\":").append(row.member().userId());
        out.append(",\"status\":\"active\"");
        out.append(",\"cohort\":").append(quote(row.member().cohort()));
        out.append(",\"profileVersion\":").append(row.version().version());
        out.append(",\"sourceVersion\":").append(row.version().sourceProfileVersion());
        out.append(",\"generation\":").append(row.generation());
        out.append(",\"consentVersion\":").append(row.consentVersion());
        out.append(",\"inputHash\":").append(quote(row.version().inputHash()));
        out.append(",\"gender\":").append(quote(candidate.gender().name()));
        out.append(",\"birthday\":").append(quote(candidate.birthday().toString()));
        out.append(",\"ageMin\":").append(candidate.ageMin());
        out.append(",\"ageMax\":").append(candidate.ageMax());
        out.append(",\"height\":").append(candidate.heightCm());
        out.append(",\"pool\":").append(quote(candidate.pool()));
        out.append(",\"wants\":[");
        List<String> wants = new ArrayList<>();
        for (GenderPref pref : candidate.wants()) wants.add(pref.name());
        wants.sort(String::compareTo);
        for (int i = 0; i < wants.size(); i++) {
            if (i > 0) out.append(',');
            out.append(quote(wants.get(i)));
        }
        out.append("],\"features\":[");
        List<String> features = new ArrayList<>();
        for (ProfileFeature feature : row.snapshot().self().features()) features.add(featureLine(feature.featureCode(), feature.status().name(), feature.value()));
        for (PreferenceTarget target : row.snapshot().preference().targets()) {
            features.add(featureLine(target.featureCode(), target.status().name(), target.targetValue()));
        }
        features.sort(String::compareTo);
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) out.append(',');
            out.append(features.get(i));
        }
        out.append("]}");
    }

    private static String featureLine(String code, String status, FeatureValue value) {
        return "{\"code\":" + quote(code) + ",\"status\":" + quote(status) + ",\"value\":" + quote(shown(value)) + "}";
    }

    private static String shown(FeatureValue value) {
        if (value instanceof FeatureValue.EnumOne one && safe(one.value())) return one.value();
        if (value instanceof FeatureValue.Bool bool) return Boolean.toString(bool.value());
        if (value instanceof FeatureValue.Num number) return Double.toString(number.value());
        if (value instanceof FeatureValue.Span span) return span.min() + ".." + span.max();
        if (value instanceof FeatureValue.EnumMany many) {
            StringBuilder out = new StringBuilder();
            for (String item : many.values()) {
                if (!safe(item)) return value.getClass().getSimpleName();
                if (!out.isEmpty()) out.append('+');
                out.append(item);
            }
            return out.toString();
        }
        if (value instanceof FeatureValue.Withheld || value instanceof FeatureValue.Unbounded) return value.getClass().getSimpleName();
        return value == null ? "ABSENT" : value.getClass().getSimpleName();
    }

    private static boolean safe(String value) {
        return value != null && value.length() <= 40 && value.matches("[A-Za-z0-9_:+.-]+");
    }

    static String emptyComparison(String reason) {
        return "{\"executable\":false,\"reason\":" + quote(reason) + "}";
    }
}
