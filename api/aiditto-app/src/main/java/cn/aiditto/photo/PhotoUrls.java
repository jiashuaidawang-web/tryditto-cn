package cn.aiditto.photo;

import cn.aiditto.infra.crypto.Crypto;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

/**
 * Capability URLs for photos. {@code <img>} cannot send the bearer token, so the API hands out
 * {@code /api/photos/{id}/raw?exp=…&sig=…} where sig = HMAC(HASH_PEPPER, id:exp). Expiry is rounded to a day so the
 * same photo keeps the same URL (and browser cache) for a while.
 */
@Component
public class PhotoUrls {
    static final Duration TTL = Duration.ofDays(7);
    private static final long DAY = Duration.ofDays(1).toSeconds();

    private final Crypto crypto;
    private final Clock clock;

    public PhotoUrls(Crypto crypto, Clock clock) {
        this.crypto = crypto;
        this.clock = clock;
    }

    public String url(long photoId) {
        long exp = (clock.instant().plus(TTL).getEpochSecond() / DAY + 1) * DAY;
        return "/api/photos/" + photoId + "/raw?exp=" + exp + "&sig=" + sign(photoId, exp);
    }

    public boolean valid(long photoId, Long exp, String sig) {
        if (exp == null || sig == null || exp < clock.instant().getEpochSecond()) return false;
        return MessageDigest.isEqual(sign(photoId, exp).getBytes(), sig.getBytes());
    }

    private String sign(long photoId, long exp) {
        byte[] mac = crypto.hash("photo:" + photoId + ":" + exp);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(mac, 16));
    }
}
