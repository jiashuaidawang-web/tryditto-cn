package cn.aiditto.taste;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Step 8 反馈学习配置。默认开启但由「每周≤1 次」与「≥30 条才校准」两道闸门限速。 */
@ConfigurationProperties("aiditto.learning")
public record FeedbackLearningProperties(
        Boolean enabled,
        Integer maxPerScan,
        Integer minCalibrationSamples,
        Double calibrationCap) {

    public FeedbackLearningProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        maxPerScan = maxPerScan == null || maxPerScan <= 0 ? 500 : Math.min(maxPerScan, 5000);
        minCalibrationSamples = minCalibrationSamples == null || minCalibrationSamples < 2
                ? 30 : Math.min(minCalibrationSamples, 1000);
        calibrationCap = calibrationCap == null || calibrationCap < 0
                ? 10.0 : Math.min(calibrationCap, 10.0);
    }
}
