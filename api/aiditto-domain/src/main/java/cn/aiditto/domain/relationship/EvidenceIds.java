package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Server-side evidence ids. Callers cannot choose one. */
public final class EvidenceIds {
    public static final String CATALOG = "catalog-1";

    private EvidenceIds() {}

    public static String compute(long userId, EvidenceSource source, String sourceRef, String sourceVersion, String featureCode) {
        return sha256(userId + "|" + source.name() + "|" + sourceRef + "|" + sourceVersion + "|" + featureCode).substring(0, 32);
    }

    public static String sha256(String raw) {
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
