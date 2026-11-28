package cn.aiditto.domain.integration;

import java.util.Locale;
import java.util.Set;

/**
 * A test integration mode starts only in a named isolated environment, off a production profile,
 * and against an isolated database. The default legacy mode does not consult those checks.
 */
public final class ProductIntegrationGate {
    private static final Set<String> HOSTS = Set.of("localhost", "127.0.0.1", "::1", "postgres");
    private static final Set<String> DATABASES = Set.of("aiditto_s01_test", "aiditto_lab_e2e");

    private ProductIntegrationGate() {}

    public static ProductIntegrationMode open(String modeRaw, String environment, String activeProfiles, String jdbcUrl) {
        ProductIntegrationMode mode = ProductIntegrationMode.parse(modeRaw);
        if (mode == ProductIntegrationMode.LEGACY_ONLY) return mode;
        if (production(activeProfiles, environment)) {
            throw new IllegalStateException("test integration mode is closed on a production profile");
        }
        if (!isolatedEnvironment(environment)) {
            throw new IllegalStateException("test integration mode requires environment local, ci, or isolated");
        }
        if (!isolatedDatabase(jdbcUrl)) {
            throw new IllegalStateException("test integration mode requires an isolated database");
        }
        return mode;
    }

    public static boolean isolatedEnvironment(String environment) {
        if (environment == null) return false;
        String name = environment.trim().toLowerCase(Locale.ROOT);
        return "local".equals(name) || "ci".equals(name) || "isolated".equals(name);
    }

    public static boolean production(String activeProfiles, String environment) {
        if (environment != null) {
            String name = environment.trim().toLowerCase(Locale.ROOT);
            if ("prod".equals(name) || "production".equals(name)) return true;
        }
        if (activeProfiles == null || activeProfiles.isBlank()) return false;
        for (String profile : activeProfiles.split(",")) {
            String name = profile.trim().toLowerCase(Locale.ROOT);
            if ("prod".equals(name) || "production".equals(name)) return true;
        }
        return false;
    }

    public static boolean isolatedDatabase(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return false;
        String url = jdbcUrl.trim();
        String prefix = "jdbc:postgresql://";
        if (!url.startsWith(prefix) || url.indexOf('@') >= 0) return false;
        String rest = url.substring(prefix.length());
        String host;
        String path;
        if (rest.startsWith("[")) {
            int end = rest.indexOf(']');
            if (end < 0) return false;
            host = rest.substring(1, end);
            path = rest.substring(end + 1);
        } else {
            int slash = rest.indexOf('/');
            if (slash < 0) return false;
            String hostPort = rest.substring(0, slash);
            int colon = hostPort.indexOf(':');
            host = colon < 0 ? hostPort : hostPort.substring(0, colon);
            path = rest.substring(slash);
        }
        int slash = path.indexOf('/');
        if (slash < 0) return false;
        String database = path.substring(slash + 1);
        int query = database.indexOf('?');
        if (query >= 0) database = database.substring(0, query);
        if (database.isBlank() || database.indexOf('/') >= 0) return false;
        if (!HOSTS.contains(host.toLowerCase(Locale.ROOT))) return false;
        return DATABASES.contains(database) || database.endsWith("_test") || database.endsWith("_e2e");
    }
}
