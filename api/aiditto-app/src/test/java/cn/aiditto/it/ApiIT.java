package cn.aiditto.it;

import cn.aiditto.AidittoApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.sql.DatabaseMetaData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests against an explicit disposable PostgreSQL database. {@code TEST_DB_URL} and
 * {@code TEST_DB_USER} are required. {@link TestDatabaseGuard} rejects every other database before clean.
 * Each class closes its context so the next class cleans a database nobody else is using.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {AidittoApplication.class, ApiIT.FromZero.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class ApiIT {
    static final String DEMO_CODE = "888888";
    static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        String url = TestDatabaseGuard.requireUrl(System.getenv("TEST_DB_URL"));
        String user = System.getenv("TEST_DB_USER");
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("TEST_DB_USER is required");
        }
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> user);
        r.add("spring.datasource.password", () -> env("TEST_DB_PASSWORD", ""));
        r.add("spring.flyway.clean-disabled", () -> "false");
        r.add("aiditto.demo-mode", () -> "true");
        r.add("aiditto.demo-login-code", () -> DEMO_CODE);
        r.add("aiditto.photos.root", () -> "target/it-photos");
        r.add("aiditto.admin-phones", () -> "13800000001");
        r.add("aiditto.scheduler.enabled", () -> "false");
        r.add("aiditto.synthetic.autoload", () -> "false");
        // Step 4 画像提取在 IT 里同步执行，避免异步任务跨用例写库造成竞态
        r.add("aiditto.taste.async-enabled", () -> "false");
        r.add("management.server.port", () -> "0");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FromZero {
        @Bean
        FlywayMigrationStrategy cleanThenMigrate() {
            return flyway -> {
                String configured;
                try {
                    configured = JdbcUtils.extractDatabaseMetaData(
                            flyway.getConfiguration().getDataSource(), DatabaseMetaData::getURL);
                } catch (MetaDataAccessException e) {
                    throw new IllegalStateException("Refusing to clean because the database URL could not be read", e);
                }
                TestDatabaseGuard.requireUrl(configured);
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    record Resp(int status, JsonNode body, HttpHeaders headers, byte[] raw) {
        String code() {
            return body == null || body.get("code") == null ? null : body.get("code").asString();
        }
    }

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    static String randomPhone() {
        return "139" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    Resp call(String method, String path, Object body, String token) {
        return send(method, path, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)), "application/json", token);
    }

    Resp send(String method, String path, HttpRequest.BodyPublisher body, String contentType, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).method(method, body);
        if (contentType != null) b.header("Content-Type", contentType);
        if (token != null) b.header("Authorization", "Bearer " + token);
        try {
            HttpResponse<byte[]> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            String type = r.headers().firstValue("Content-Type").orElse("");
            JsonNode json = r.body().length > 0 && type.contains("json") ? JSON.readTree(r.body()) : null;
            return new Resp(r.statusCode(), json, r.headers(), r.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    String login(String phone) {
        Resp r = call("POST", "/api/auth/login", Map.of("phone", phone, "code", DEMO_CODE), null);
        assertThat(r.status()).as("login %s", r.body()).isEqualTo(200);
        return r.body().get("token").asString();
    }

    void answer(String token, String qid, Object value) {
        Resp r = call("PUT", "/api/onboarding/answers/" + qid, java.util.Collections.singletonMap("value", value), token);
        assertThat(r.status()).as("answer %s → %s", qid, r.body()).isEqualTo(204);
    }

    /** Three-step upload of a generated PNG; returns the finalize response. */
    JsonNode uploadPhoto(String token, String kind, int width, int height) {
        byte[] png = png(width, height);
        Resp ticket = call("POST", "/api/photos/upload-url", Map.of("kind", kind, "contentType", "image/png", "bytes", png.length), token);
        assertThat(ticket.status()).as("upload-url %s", ticket.body()).isEqualTo(200);
        String uploadId = ticket.body().get("uploadId").asString();
        Resp put = send("PUT", ticket.body().get("url").asString(), HttpRequest.BodyPublishers.ofByteArray(png), "image/png", token);
        assertThat(put.status()).isEqualTo(204);
        Resp fin = call("POST", "/api/photos/finalize", Map.of("uploadId", uploadId), token);
        assertThat(fin.status()).as("finalize %s", fin.body()).isEqualTo(200);
        return fin.body();
    }

    static byte[] png(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(ThreadLocalRandom.current().nextInt(0xffffff)));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
