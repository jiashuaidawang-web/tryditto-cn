package cn.aiditto.match;

import cn.aiditto.auth.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/api/admin")
public class AdminMatchController {
    private final WeekRunner weeks;
    private final MatchDb db;
    private final SyntheticLoader synthetic;
    private final DemoService demo;
    private final Clock clock;

    public AdminMatchController(WeekRunner weeks, MatchDb db, SyntheticLoader synthetic, DemoService demo, Clock clock) {
        this.weeks = weeks;
        this.db = db;
        this.synthetic = synthetic;
        this.demo = demo;
        this.clock = clock;
    }

    record RunBody(String pool, Boolean publish, Boolean dryRun) {}

    @PostMapping("/match-runs")
    Map<String, Object> run(@AuthenticationPrincipal AuthUser me, @RequestBody RunBody body, HttpServletRequest req) {
        boolean dry = body.dryRun() != null && body.dryRun();
        boolean publish = body.publish() == null || body.publish();
        List<String> pools = body.pool() == null || body.pool().isBlank() ? List.of("bj", "sh") : List.of(body.pool());
        long t0 = System.nanoTime();
        List<Map<String, Object>> runs = runPools(pools, dry, publish);
        long millis = (System.nanoTime() - t0) / 1_000_000;
        db.audit(me.userId(), dry ? "match.dry-run" : "match.run", "week", pools.toString(),
                "{\"millis\":" + millis + "}", req.getRemoteAddr(), clock.instant());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", runs);
        out.put("millis", millis);
        return out;
    }

    /** The two cities do not share rows, so a full run overlaps them. */
    private List<Map<String, Object>> runPools(List<String> pools, boolean dry, boolean publish) {
        if (pools.size() == 1) return List.of(one(pools.get(0), dry, publish));
        ExecutorService pool = Executors.newFixedThreadPool(pools.size());
        try {
            List<Future<Map<String, Object>>> waiting = new ArrayList<>();
            for (String city : pools) waiting.add(pool.submit(() -> one(city, dry, publish)));
            List<Map<String, Object>> out = new ArrayList<>();
            for (Future<Map<String, Object>> f : waiting) out.add(f.get());
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException r) throw r;
            throw new IllegalStateException(cause);
        } finally {
            pool.shutdown();
        }
    }

    private Map<String, Object> one(String pool, boolean dry, boolean publish) {
        return dry ? weeks.dryRun(pool, clock.instant()) : weeks.runManual(pool, clock.instant(), publish);
    }

    @GetMapping("/weeks")
    List<Map<String, Object>> weeks() {
        return db.weeks();
    }

    @GetMapping("/weeks/{id}")
    Map<String, Object> week(@PathVariable long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("week", db.weeks().stream().filter(w -> id == Long.parseLong(w.get("id").toString())).findFirst().orElse(null));
        out.put("pairs", db.pairs(id));
        return out;
    }

    @GetMapping("/matches")
    List<Map<String, Object>> matches(@RequestParam long weekId) {
        return db.pairs(weekId);
    }

    @PostMapping("/synthetic/load")
    Map<String, Integer> load(@AuthenticationPrincipal AuthUser me, HttpServletRequest req) {
        Map<String, Integer> out = synthetic.load();
        db.audit(me.userId(), "synthetic.load", "user", null, null, req.getRemoteAddr(), clock.instant());
        return out;
    }

    @PostMapping("/demo/reset")
    Map<String, Object> reset(@AuthenticationPrincipal AuthUser me, HttpServletRequest req) {
        int removed = demo.resetNonReal(clock.instant());
        db.audit(me.userId(), "demo.reset", "match", null, "{\"removed\":" + removed + "}", req.getRemoteAddr(), clock.instant());
        return Map.of("removed", removed);
    }
}
