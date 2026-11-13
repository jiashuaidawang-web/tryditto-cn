package cn.aiditto.infra.crypto;

import cn.aiditto.infra.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Column encryption and lookup hashes (技术方案 §4.1, §9). Ciphertext = key version (1 byte) + IV (12) + AES-256-GCM.
 * Hashes are HMAC-SHA256 with HASH_PEPPER so equal values can be matched without storing them.
 */
@Component
public class Crypto {
    private static final Logger log = LoggerFactory.getLogger(Crypto.class);
    private static final byte KEY_VERSION = 1;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec dataKey;
    private final SecretKeySpec pepper;
    private final SecureRandom random = new SecureRandom();

    public Crypto(AppProperties props) {
        AppProperties.Crypto c = props.crypto();
        boolean dev = c == null || c.allowDevKeys();
        String dataKey = c == null ? null : c.dataKey();
        String pepper = c == null ? null : c.hashPepper();
        if (isBlank(dataKey) || isBlank(pepper)) {
            if (!dev) throw new IllegalStateException("DATA_KEY and HASH_PEPPER must be set");
            log.warn("DATA_KEY / HASH_PEPPER not set: using development-only derived keys");
        }
        byte[] key = isBlank(dataKey) ? sha256("aiditto-dev-only-data-key".getBytes(StandardCharsets.UTF_8)) : Base64.getDecoder().decode(dataKey.strip());
        if (key.length != 32) throw new IllegalStateException("DATA_KEY must be 32 bytes, base64-encoded");
        byte[] pep = isBlank(pepper) ? "aiditto-dev-only-pepper".getBytes(StandardCharsets.UTF_8) : pepper.strip().getBytes(StandardCharsets.UTF_8);
        if (!isBlank(pepper) && pep.length < 16) throw new IllegalStateException("HASH_PEPPER must be at least 16 characters");
        this.dataKey = new SecretKeySpec(key, "AES");
        this.pepper = new SecretKeySpec(pep, "HmacSHA256");
    }

    public byte[] encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(new byte[] {KEY_VERSION});
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_LEN + ct.length).put(KEY_VERSION).put(iv).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(byte[] blob) {
        if (blob == null) return null;
        if (blob.length < 1 + IV_LEN + 16 || blob[0] != KEY_VERSION) throw new IllegalStateException("unknown ciphertext version");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, blob, 1, IV_LEN));
            cipher.updateAAD(new byte[] {blob[0]});
            byte[] pt = cipher.doFinal(blob, 1 + IV_LEN, blob.length - 1 - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }

    /** HMAC-SHA256(HASH_PEPPER, value). Callers normalize first (phone digits, lower-case email). */
    public byte[] hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(pepper);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("hash failed", e);
        }
    }

    public String hashHex(String value) {
        return java.util.HexFormat.of().formatHex(hash(value));
    }

    public static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        random.nextBytes(b);
        return b;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
