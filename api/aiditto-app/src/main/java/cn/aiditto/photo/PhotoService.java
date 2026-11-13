package cn.aiditto.photo;

import cn.aiditto.account.User;
import cn.aiditto.account.UserLock;
import cn.aiditto.account.UserMapper;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.questionnaire.OnboardingRules;
import cn.aiditto.infra.config.AppProperties;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.web.Times;
import cn.aiditto.profile.ProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Three-step upload on a local volume (技术方案 §6.5, §10.3): upload-url reserves a pending row, the blob PUT lands in
 * tmp/, finalize re-encodes to JPEG under u/{userId}/{photoId}.jpg. nginx serves files via X-Accel-Redirect in prod.
 */
@Service
public class PhotoService {
    public static final int MAX_PER_KIND = 6;
    static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    static final Set<String> KINDS = Set.of("profile", "ideal");
    static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public record UploadTicket(String uploadId, String url, String method, String expiresAt) {}

    public record PhotoView(String id, String url, String kind, int position) {}

    private final PhotoMapper photos;
    private final UserMapper users;
    private final UserLock userLock;
    private final ProfileMapper profiles;
    private final PhotoUrls urls;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final Clock clock;
    private final Path root;

    public PhotoService(PhotoMapper photos, UserMapper users, UserLock userLock, ProfileMapper profiles, PhotoUrls urls,
                        TransactionTemplate tx, AppProperties props, Clock clock) throws IOException {
        this.photos = photos;
        this.users = users;
        this.userLock = userLock;
        this.profiles = profiles;
        this.urls = urls;
        this.tx = tx;
        this.props = props;
        this.clock = clock;
        this.root = Path.of(props.photos().root()).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("tmp"));
        ImageProcessor.warmUp();
    }

    public UploadTicket uploadUrl(long userId, String kind, String contentType, long bytes) {
        requireKind(kind);
        if (contentType == null || !TYPES.contains(contentType.toLowerCase())) throw new DomainException(ErrorCode.PHOTO_TYPE, "只支持 JPEG、PNG、WebP");
        if (bytes <= 0 || bytes > props.photos().maxBytes()) throw new DomainException(ErrorCode.PHOTO_TOO_LARGE, "照片不能超过 8 MB");
        if (countActive(userId, kind) >= MAX_PER_KIND) throw new DomainException(ErrorCode.PHOTO_LIMIT);
        Instant now = Times.millis(clock.instant());
        Photo p = new Photo();
        p.userId = userId;
        p.kind = kind;
        p.position = 0;
        p.status = Photo.PENDING;
        p.contentType = contentType.toLowerCase();
        p.moderation = "skipped";
        p.createdAt = now;
        photos.insert(p);
        return new UploadTicket(String.valueOf(p.id), "/api/photos/blob/" + p.id, "PUT", Times.iso(now.plus(UPLOAD_TTL)));
    }

    public void receiveBlob(long userId, long uploadId, InputStream body) {
        Photo p = pending(userId, uploadId);
        Path tmp = tmpPath(p.id);
        long max = props.photos().maxBytes();
        try (OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            for (int n; (n = body.read(buf)) > 0; ) {
                total += n;
                if (total > max) {
                    out.close();
                    Files.deleteIfExists(tmp);
                    throw new DomainException(ErrorCode.PHOTO_TOO_LARGE, "照片不能超过 8 MB");
                }
                out.write(buf, 0, n);
            }
            if (total == 0) throw new DomainException(ErrorCode.VALIDATION, "上传内容为空");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PhotoView finalizeUpload(long userId, long uploadId) {
        Photo p = pending(userId, uploadId);
        Path tmp = tmpPath(p.id);
        if (!Files.exists(tmp)) throw new DomainException(ErrorCode.UPLOAD_STATE, "还没有上传文件");
        ImageProcessor.Result img = ImageProcessor.toJpeg(tmp);
        String key = "u/" + userId + "/" + p.id + ".jpg";
        Path target = root.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path part = target.resolveSibling(p.id + ".jpg.part");
            Files.write(part, img.jpeg());
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int position = tx.execute(s -> {
            userLock.lock(userId);
            long active = countActive(userId, p.kind);
            if (active >= MAX_PER_KIND) throw new DomainException(ErrorCode.PHOTO_LIMIT);
            int pos = (int) active;
            int updated = photos.update(null, new UpdateWrapper<Photo>().eq("id", p.id).eq("status", Photo.PENDING)
                    .set("status", Photo.ACTIVE).set("storage_key", key).set("content_type", "image/jpeg")
                    .set("width", img.width()).set("height", img.height()).set("bytes", img.jpeg().length)
                    .set("sha256", Crypto.sha256(img.jpeg())).set("position", pos));
            if (updated == 0) throw new DomainException(ErrorCode.UPLOAD_STATE);
            syncPhotoCount(userId);
            return pos;
        });
        return new PhotoView(String.valueOf(p.id), urls.url(p.id), p.kind, position);
    }

    public List<PhotoView> list(long userId, String kind) {
        requireKind(kind);
        return active(userId, kind).stream().map(p -> new PhotoView(String.valueOf(p.id), urls.url(p.id), p.kind, p.position)).toList();
    }

    public void reorder(long userId, String kind, List<String> ids) {
        requireKind(kind);
        tx.executeWithoutResult(s -> {
            userLock.lock(userId);
            List<Photo> current = active(userId, kind);
            Set<String> have = new HashSet<>();
            current.forEach(p -> have.add(String.valueOf(p.id)));
            if (ids == null || ids.size() != current.size() || !have.equals(new HashSet<>(ids))) {
                throw new DomainException(ErrorCode.VALIDATION, "ids 必须正好是全部照片", List.of("ids"), null);
            }
            for (int i = 0; i < ids.size(); i++) {
                photos.update(null, new UpdateWrapper<Photo>().eq("id", Long.parseLong(ids.get(i))).set("position", i));
            }
        });
    }

    public void delete(long userId, long photoId) {
        tx.executeWithoutResult(s -> {
            userLock.lock(userId);
            Photo p = photos.selectById(photoId);
            if (p == null || p.userId != userId || !Photo.ACTIVE.equals(p.status)) throw new DomainException(ErrorCode.NOT_FOUND);
            User u = users.selectById(userId);
            if ("profile".equals(p.kind) && u != null && u.onboarded() && countActive(userId, "profile") <= OnboardingRules.MIN_PHOTOS) {
                throw new DomainException(ErrorCode.PHOTO_MIN);
            }
            photos.update(null, new UpdateWrapper<Photo>().eq("id", photoId).set("status", Photo.DELETED)
                    .set("deleted_at", Times.millis(clock.instant())));
            List<Photo> rest = active(userId, p.kind);
            for (int i = 0; i < rest.size(); i++) {
                if (rest.get(i).position != i) photos.update(null, new UpdateWrapper<Photo>().eq("id", rest.get(i).id).set("position", i));
            }
            syncPhotoCount(userId);
        });
    }

    public long countActive(long userId, String kind) {
        return photos.selectCount(new QueryWrapper<Photo>().eq("user_id", userId).eq("kind", kind).eq("status", Photo.ACTIVE));
    }

    /** The file behind an active photo, or null. */
    public Photo activePhoto(long photoId) {
        Photo p = photos.selectById(photoId);
        return p != null && Photo.ACTIVE.equals(p.status) ? p : null;
    }

    public Path fileOf(Photo p) {
        Path f = root.resolve(p.storageKey).normalize();
        if (!f.startsWith(root)) throw new DomainException(ErrorCode.NOT_FOUND);
        return f;
    }

    private void syncPhotoCount(long userId) {
        profiles.setPhotoCount(userId, countActive(userId, "profile"));
    }

    private List<Photo> active(long userId, String kind) {
        return photos.selectList(new QueryWrapper<Photo>().eq("user_id", userId).eq("kind", kind).eq("status", Photo.ACTIVE)
                .orderByAsc("position").orderByAsc("id"));
    }

    private Photo pending(long userId, long uploadId) {
        Photo p = photos.selectById(uploadId);
        if (p == null || p.userId != userId) throw new DomainException(ErrorCode.NOT_FOUND);
        if (!Photo.PENDING.equals(p.status) || p.createdAt.plus(UPLOAD_TTL).isBefore(clock.instant())) {
            throw new DomainException(ErrorCode.UPLOAD_STATE);
        }
        return p;
    }

    private Path tmpPath(long id) {
        return root.resolve("tmp").resolve(id + ".upload");
    }

    private static void requireKind(String kind) {
        if (kind == null || !KINDS.contains(kind)) throw DomainException.invalid("kind", "kind 只能是 profile 或 ideal");
    }
}
