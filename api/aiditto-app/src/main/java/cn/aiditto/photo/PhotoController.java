package cn.aiditto.photo;

import cn.aiditto.auth.AuthUser;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.infra.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {
    private final PhotoService photos;
    private final PhotoUrls urls;
    private final AppProperties props;

    public PhotoController(PhotoService photos, PhotoUrls urls, AppProperties props) {
        this.photos = photos;
        this.urls = urls;
        this.props = props;
    }

    record UploadUrlRequest(String kind, String contentType, Long bytes) {}

    record OrderRequest(String kind, List<String> ids) {}

    record FinalizeRequest(String uploadId) {}

    @PostMapping("/upload-url")
    PhotoService.UploadTicket uploadUrl(@AuthenticationPrincipal AuthUser me, @RequestBody UploadUrlRequest body) {
        return photos.uploadUrl(me.userId(), body.kind(), body.contentType(), body.bytes() == null ? 0 : body.bytes());
    }

    @PutMapping("/blob/{uploadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void blob(@AuthenticationPrincipal AuthUser me, @PathVariable long uploadId, HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > props.photos().maxBytes()) throw new DomainException(ErrorCode.PHOTO_TOO_LARGE, "照片不能超过 8 MB");
        photos.receiveBlob(me.userId(), uploadId, request.getInputStream());
    }

    @PostMapping("/finalize")
    PhotoService.PhotoView finalizeUpload(@AuthenticationPrincipal AuthUser me, @RequestBody FinalizeRequest body) {
        if (body.uploadId() == null || !body.uploadId().matches("^\\d{1,19}$")) throw DomainException.invalid("uploadId", "uploadId 不对");
        return photos.finalizeUpload(me.userId(), Long.parseLong(body.uploadId()));
    }

    @GetMapping
    List<PhotoService.PhotoView> list(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "profile") String kind) {
        return photos.list(me.userId(), kind);
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void order(@AuthenticationPrincipal AuthUser me, @RequestBody OrderRequest body) {
        photos.reorder(me.userId(), body.kind(), body.ids());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        photos.delete(me.userId(), id);
    }

    /** Signed URL or the owner's bearer token. Matched partners' access arrives with M2. */
    @GetMapping("/{id}/raw")
    ResponseEntity<Resource> raw(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                                 @RequestParam(required = false) Long exp, @RequestParam(required = false) String sig) {
        Photo p = photos.activePhoto(id);
        boolean allowed = p != null && (urls.valid(id, exp, sig) || (me != null && me.userId() == p.userId));
        if (!allowed) throw new DomainException(ErrorCode.NOT_FOUND);
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff");
        if (props.photos().accelRedirect()) {
            return ok.header("X-Accel-Redirect", "/_photos/" + p.storageKey).build();
        }
        Path file = photos.fileOf(p);
        if (!Files.isRegularFile(file)) throw new DomainException(ErrorCode.NOT_FOUND);
        return ok.body(new FileSystemResource(file));
    }
}
