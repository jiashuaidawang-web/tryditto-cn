package cn.aiditto.it;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoIT extends ApiIT {

    @Test
    void uploadReencodesToJpegAndServesBySignedUrl() throws IOException {
        String token = login(randomPhone());
        JsonNode photo = uploadPhoto(token, "profile", 2400, 1600);
        assertThat(photo.get("kind").asString()).isEqualTo("profile");
        assertThat(photo.get("position").asInt()).isZero();
        String url = photo.get("url").asString();
        assertThat(url).matches("/api/photos/\\d+/raw\\?exp=\\d+&sig=[A-Za-z0-9_-]+");

        Resp raw = send("GET", url, HttpRequest.BodyPublishers.noBody(), null, null);
        assertThat(raw.status()).isEqualTo(200);
        assertThat(raw.headers().firstValue("Content-Type").orElse("")).startsWith("image/jpeg");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw.raw()));
        assertThat(img.getWidth()).isEqualTo(1440);
        assertThat(img.getHeight()).isEqualTo(960);

        String tampered = url.replaceAll("sig=.*", "sig=AAAAAAAAAAAAAAAAAAAAAA");
        assertThat(send("GET", tampered, HttpRequest.BodyPublishers.noBody(), null, null).status()).isEqualTo(404);
        assertThat(send("GET", tampered, HttpRequest.BodyPublishers.noBody(), null, token).status()).isEqualTo(200);
        String stranger = login(randomPhone());
        assertThat(send("GET", tampered, HttpRequest.BodyPublishers.noBody(), null, stranger).status()).isEqualTo(404);
    }

    @Test
    void rejectsBadTypesSizesAndGarbage() {
        String token = login(randomPhone());
        Resp type = call("POST", "/api/photos/upload-url", Map.of("kind", "profile", "contentType", "image/gif", "bytes", 100), token);
        assertThat(type.status()).isEqualTo(415);
        assertThat(type.code()).isEqualTo("PHOTO_TYPE");
        Resp big = call("POST", "/api/photos/upload-url", Map.of("kind", "profile", "contentType", "image/jpeg", "bytes", 9_000_000), token);
        assertThat(big.status()).isEqualTo(413);
        assertThat(big.code()).isEqualTo("PHOTO_TOO_LARGE");

        Resp ticket = call("POST", "/api/photos/upload-url", Map.of("kind", "profile", "contentType", "image/jpeg", "bytes", 10), token);
        String id = ticket.body().get("uploadId").asString();
        send("PUT", "/api/photos/blob/" + id, HttpRequest.BodyPublishers.ofByteArray("not an image".getBytes()), "image/jpeg", token);
        Resp fin = call("POST", "/api/photos/finalize", Map.of("uploadId", id), token);
        assertThat(fin.status()).isEqualTo(415);

        String other = login(randomPhone());
        assertThat(send("PUT", "/api/photos/blob/" + id, HttpRequest.BodyPublishers.ofByteArray(png(10, 10)), "image/png", other).status()).isEqualTo(404);
    }

    @Test
    void orderDeleteAndLimit() {
        String token = login(randomPhone());
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(uploadPhoto(token, "profile", 300, 300).get("id").asString());
        Resp full = call("POST", "/api/photos/upload-url", Map.of("kind", "profile", "contentType", "image/png", "bytes", 1000), token);
        assertThat(full.status()).isEqualTo(409);
        assertThat(full.code()).isEqualTo("PHOTO_LIMIT");

        List<String> reversed = new ArrayList<>(ids);
        java.util.Collections.reverse(reversed);
        assertThat(call("PUT", "/api/photos/order", Map.of("kind", "profile", "ids", reversed), token).status()).isEqualTo(204);
        JsonNode list = call("GET", "/api/photos?kind=profile", null, token).body();
        assertThat(list.get(0).get("id").asString()).isEqualTo(ids.get(5));
        assertThat(call("PUT", "/api/photos/order", Map.of("kind", "profile", "ids", ids.subList(0, 3)), token).status()).isEqualTo(422);

        assertThat(call("DELETE", "/api/photos/" + ids.get(5), null, token).status()).isEqualTo(204);
        JsonNode after = call("GET", "/api/photos?kind=profile", null, token).body();
        assertThat(after.size()).isEqualTo(5);
        assertThat(after.get(0).get("position").asInt()).isZero();
        assertThat(after.get(0).get("id").asString()).isEqualTo(ids.get(4));

        uploadPhoto(token, "ideal", 300, 300);
        assertThat(call("GET", "/api/photos?kind=ideal", null, token).body().size()).isEqualTo(1);
    }
}
