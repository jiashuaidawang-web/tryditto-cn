package cn.aiditto.match;

import cn.aiditto.account.AccountService;
import cn.aiditto.account.User;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.copy.Labels;
import cn.aiditto.domain.copy.Lang;
import cn.aiditto.domain.match.FeedbackAnswer;
import cn.aiditto.domain.match.MatchSnapshot;
import cn.aiditto.domain.match.MatchState;
import cn.aiditto.domain.match.ParticipationStatus;
import cn.aiditto.domain.match.SideSnapshot;
import cn.aiditto.domain.match.Stage;
import cn.aiditto.domain.match.StageProjector;
import cn.aiditto.domain.slot.Slot;
import cn.aiditto.domain.week.WeekCalendar;
import cn.aiditto.domain.week.WeekState;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The match card the home / reveal / date pages read. Strings are one language; the web client wraps them. */
@Service
public class MatchCard {
    private static final Map<String, Object> AVATAR = Map.of(
            "bg", List.of("#ffb4a2", "#ffd6a5"),
            "skin", "#f6d1b5",
            "hair", "bob",
            "hairColor", "#2b1d1a",
            "shirt", "#5b8def");

    private final MatchDb db;
    private final People people;
    private final AccountService accounts;
    private final Crypto crypto;

    public MatchCard(MatchDb db, People people, AccountService accounts, Crypto crypto) {
        this.db = db;
        this.people = people;
        this.accounts = accounts;
        this.crypto = crypto;
    }

    public Map<String, Object> current(long userId, Instant now) {
        User me = accounts.require(userId);
        if (!me.onboarded()) throw new DomainException(ErrorCode.ONBOARDING_REQUIRED);
        MatchDb.LoadedMatch match = db.latestFor(userId);
        MatchDb.WeekRow week = match == null ? null : db.weekOf(match.weekId());
        if (match != null && match.snapshot().state() == MatchState.DRAFT) {
            match = null;
        }
        if (week == null) {
            week = db.demoWeek(userId);
            if (week == null) week = db.latestCityWeek(userId);
        }
        boolean published = week != null && (week.status().state() == WeekState.PUBLISHED || week.status().state() == WeekState.CLOSED);
        ParticipationStatus part = participation(me);
        MatchSnapshot snap = match == null ? null : match.snapshot();
        Stage stage = StageProjector.project(new StageProjector.Input(userId, snap, published, part, Times.millis(now)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("match", matchJson(userId, match, snap, stage, now));
        boolean showPartner = snap != null && stage != Stage.WAITING && stage != Stage.EMPTY_WEEK && stage != Stage.PAUSED && stage != Stage.SUSPENDED;
        body.put("partner", showPartner ? partner(userId, snap, match) : null);
        body.put("reasons", showPartner ? reasons(match.id(), userId, nameOf(userId), partnerName(snap, userId)) : List.of());
        body.put("interests", showPartner ? interests(userId, snap.partnerOf(userId).userId()) : List.of());
        body.put("partnerWantsAgain", match != null && db.wantsAgain(match.id()));
        return body;
    }

    public Map<String, Object> contact(long userId, long matchId, Instant now) {
        User me = accounts.require(userId);
        if (!me.onboarded()) throw new DomainException(ErrorCode.ONBOARDING_REQUIRED);
        MatchDb.LoadedMatch match = db.latestFor(userId);
        if (match == null || match.id() != matchId) throw new DomainException(ErrorCode.MATCH_NOT_FOUND);
        MatchDb.WeekRow week = db.weekOf(match.weekId());
        boolean published = week != null && (week.status().state() == WeekState.PUBLISHED || week.status().state() == WeekState.CLOSED);
        Stage stage = StageProjector.project(new StageProjector.Input(userId, match.snapshot(), published, participation(me), Times.millis(now)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wechat", null);
        if (!StageProjector.contactVisible(stage)) return body;
        long partnerId = match.snapshot().partnerOf(userId).userId();
        List<byte[]> rows = db.jdbc().query("SELECT wechat_enc FROM profile WHERE user_id = ?", (rs, n) -> rs.getBytes(1), partnerId);
        if (!rows.isEmpty() && rows.get(0) != null) body.put("wechat", crypto.decrypt(rows.get(0)));
        return body;
    }

    private Map<String, Object> matchJson(long userId, MatchDb.LoadedMatch match, MatchSnapshot snap, Stage stage, Instant now) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (snap == null) {
            m.put("id", "0");
            m.put("partnerId", "");
            m.put("stage", stage.legacyWire());
            m.put("droppedAt", Times.iso(now));
            m.put("respondBy", Times.iso(now));
            m.put("slots", List.of());
            m.put("mySlotIds", List.of());
            return m;
        }
        SideSnapshot mine = snap.side(userId);
        SideSnapshot theirs = snap.partnerOf(userId);
        m.put("id", Long.toString(match.id()));
        m.put("partnerId", partnerKey(theirs.userId()));
        m.put("stage", stage.legacyWire());
        m.put("droppedAt", Times.iso(snap.publishedAt() == null ? now : snap.publishedAt()));
        m.put("respondBy", Times.iso(snap.respondDue() == null ? now : snap.respondDue()));
        List<Map<String, String>> slots = new ArrayList<>();
        for (Slot s : snap.slots()) slots.add(Map.of("id", s.id(), "startsAt", Times.iso(s.startsAt())));
        m.put("slots", slots);
        m.put("mySlotIds", new ArrayList<>(mine.slotIds()));
        if (snap.confirmedSlotId() != null) m.put("confirmedSlotId", snap.confirmedSlotId());
        Map<String, Object> side = sideRow(match.id(), userId);
        if (side.get("decline") != null) m.put("declineReasons", Json.readStringList(side.get("decline").toString()));
        if (side.get("note") != null) m.put("declineNote", side.get("note"));
        m.put("flaggedReasons", match.flagged().getOrDefault(userId, List.of()));
        if (mine.checkedInAt() != null) m.put("checkedInAt", Times.iso(mine.checkedInAt()));
        if (mine.feedback() != null) m.put("feedback", feedback(match.id(), userId, mine.feedback()));
        String category = reportCategory(match.id(), userId);
        if (category != null) m.put("reportCategory", category);
        return m;
    }

    private Map<String, Object> partner(long viewerId, MatchSnapshot snap, MatchDb.LoadedMatch match) {
        long partnerId = snap.partnerOf(viewerId).userId();
        People.Member member = people.one(partnerId);
        Map<String, Object> card = member == null || member.personaJson() == null ? Map.of() : Json.readMap(member.personaJson());
        String myName = nameOf(viewerId);
        String theirName = text(card.get("name"), member == null ? "" : member.facts().nickname());
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", partnerKey(partnerId));
        p.put("name", theirName);
        p.put("gender", member != null && member.candidate() != null ? member.candidate().gender().id() : genderOf(partnerId));
        p.put("age", ageOf(partnerId));
        p.put("school", text(card.get("school"), member == null || member.placeNameZh() == null ? "" : member.placeNameZh()));
        p.put("major", text(card.get("major"), member == null || member.occupation() == null ? "" : member.occupation()));
        p.put("year", text(card.get("year"), ""));
        p.put("heightCm", member == null || member.heightCm() <= 0 ? 170 : member.heightCm());
        p.put("hobbies", member == null ? List.of() : member.hobbies());
        p.put("extraTags", tags(card.get("extraTags")));
        p.put("avatar", card.get("avatar") instanceof Map<?, ?> ? card.get("avatar") : AVATAR);
        p.put("scenes", scenes(card.get("scenes")));
        Copy copy = copyOf(match.id(), viewerId);
        p.put("headline", fill(copy.headline, myName, theirName));
        p.put("blurb", copy.blurb.stream().map(s -> fill(s, myName, theirName)).toList());
        p.put("wechat", "");
        p.put("venue", venue(match, copy));
        p.put("openers", copy.openers.stream().map(s -> fill(s, myName, theirName)).toList());
        return p;
    }

    private List<Map<String, Object>> reasons(long matchId, long viewerId, String me, String partner) {
        List<Map<String, Object>> out = new ArrayList<>();
        Copy copy = copyOf(matchId, viewerId);
        // Step 7：文案来源由 match_copy.source 决定（'ai' = AI 生成，'template' = 模板兜底）。
        // 前端用 ai 标记显示「由 AI 生成」提示；本项目是 Agent 产品，如实展示而非隐藏。
        boolean ai = copy.source() != null && "ai".equalsIgnoreCase(copy.source().strip());
        for (Map<String, Object> r : copy.reasons) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("text", fill(String.valueOf(r.getOrDefault("text", "")), me, partner));
            one.put("basis", fill(String.valueOf(r.getOrDefault("basis", "")), me, partner));
            one.put("ai", ai);
            out.add(one);
        }
        return out;
    }

    private List<Map<String, Object>> interests(long viewerId, long partnerId) {
        People.Member me = people.one(viewerId);
        People.Member them = people.one(partnerId);
        List<String> mine = me == null ? List.of() : me.hobbies();
        List<Map<String, Object>> out = new ArrayList<>();
        if (them != null) {
            for (String h : them.hobbies()) {
                String label = Labels.hobby(h, Lang.ZH);
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("label", label == null ? h : label);
                one.put("shared", mine.contains(h));
                out.add(one);
            }
        }
        Map<String, Object> card = them == null || them.personaJson() == null ? Map.of() : Json.readMap(them.personaJson());
        for (String tag : tags(card.get("extraTags"))) {
            out.add(Map.of("label", tag, "shared", false));
        }
        return out;
    }

    private Map<String, Object> venue(MatchDb.LoadedMatch match, Copy copy) {
        Map<String, Object> v = new LinkedHashMap<>();
        Map<String, Object> stored = match.venueJson() == null || match.venueJson().isBlank() || "{}".equals(match.venueJson())
                ? Map.of() : Json.readMap(match.venueJson());
        v.put("id", String.valueOf(stored.getOrDefault("id", "venue")));
        v.put("name", String.valueOf(stored.getOrDefault("name", "咖啡馆")));
        v.put("address", String.valueOf(stored.getOrDefault("address", "")));
        Object activity = match.activity() != null ? match.activity() : stored.getOrDefault("activity", "");
        Object extra = match.extra() != null ? match.extra() : stored.getOrDefault("extra", "");
        v.put("activity", activity == null ? "" : activity.toString());
        v.put("extra", extra == null ? "" : extra.toString());
        return v;
    }

    private Map<String, Object> feedback(long matchId, long userId, FeedbackAnswer f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("met", f.met().id());
        if (f.rating() != null) m.put("rating", f.rating());
        if (f.again() != null) m.put("again", f.again().id());
        if (f.feltSafe() != null) m.put("feltSafe", f.feltSafe());
        List<byte[]> notes = db.jdbc().query("SELECT comment_enc FROM feedback WHERE match_id = ? AND user_id = ?",
                (rs, n) -> rs.getBytes(1), matchId, userId);
        if (!notes.isEmpty() && notes.get(0) != null) m.put("comment", crypto.decrypt(notes.get(0)));
        return m;
    }

    private Copy copyOf(long matchId, long viewerId) {
        List<Copy> rows = db.jdbc().query("SELECT headline, blurb, reasons, openers, source FROM match_copy WHERE match_id = ? AND viewer_id = ?",
                (rs, n) -> new Copy(rs.getString(1), readStrings(rs.getString(2)), readMaps(rs.getString(3)),
                        readStrings(rs.getString(4)), rs.getString(5)),
                matchId, viewerId);
        return rows.isEmpty() ? new Copy("", List.of(), List.of(), List.of(), null) : rows.get(0);
    }

    private Map<String, Object> sideRow(long matchId, long userId) {
        List<Map<String, Object>> rows = db.jdbc().query(
                "SELECT decline_reasons, decline_note FROM match_side WHERE match_id = ? AND user_id = ?",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("decline", rs.getString(1));
                    m.put("note", rs.getString(2));
                    return m;
                }, matchId, userId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private String reportCategory(long matchId, long userId) {
        List<String> rows = db.jdbc().query("SELECT category FROM report WHERE match_id = ? AND reporter_id = ? ORDER BY id DESC LIMIT 1",
                (rs, n) -> rs.getString(1), matchId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String partnerKey(long userId) {
        List<String> handles = db.jdbc().query("SELECT handle FROM \"user\" WHERE id = ?", (rs, n) -> rs.getString(1), userId);
        if (!handles.isEmpty() && handles.get(0) != null && !handles.get(0).isBlank()) return handles.get(0);
        return "u" + userId;
    }

    private String partnerName(MatchSnapshot snap, long viewerId) {
        return nameOf(snap.partnerOf(viewerId).userId());
    }

    private String nameOf(long userId) {
        return people.nameOf(userId);
    }

    private String genderOf(long userId) {
        List<String> rows = db.jdbc().query("SELECT gender FROM profile WHERE user_id = ?", (rs, n) -> rs.getString(1), userId);
        return rows.isEmpty() || rows.get(0) == null ? "nonbinary" : rows.get(0);
    }

    private int ageOf(long userId) {
        List<LocalDate> rows = db.jdbc().query("SELECT birthday FROM profile WHERE user_id = ?",
                (rs, n) -> rs.getObject(1, LocalDate.class), userId);
        if (rows.isEmpty() || rows.get(0) == null) return 21;
        return Period.between(rows.get(0), LocalDate.now(WeekCalendar.ZONE)).getYears();
    }

    private static ParticipationStatus participation(User u) {
        if ("suspended".equals(u.status)) return ParticipationStatus.SUSPENDED;
        if ("paused".equals(u.status)) return ParticipationStatus.PAUSED;
        return ParticipationStatus.ACTIVE;
    }

    private static String text(Object node, String fallback) {
        if (node instanceof Map<?, ?> m) {
            Object zh = m.get("zh");
            if (zh != null && !zh.toString().isBlank()) return zh.toString();
            Object en = m.get("en");
            if (en != null) return en.toString();
        }
        if (node instanceof String s && !s.isBlank()) return s;
        return fallback == null ? "" : fallback;
    }

    private static List<String> tags(Object node) {
        List<String> out = new ArrayList<>();
        if (node instanceof List<?> list) {
            for (Object o : list) out.add(text(o, ""));
        }
        out.removeIf(String::isBlank);
        return out;
    }

    private static List<String> scenes(Object node) {
        List<String> out = new ArrayList<>();
        if (node instanceof List<?> list) for (Object o : list) if (o != null) out.add(o.toString());
        if (out.size() < 2) {
            if (!out.contains("coffee")) out.add("coffee");
            if (out.size() < 2) out.add("books");
        }
        return out;
    }

    private static String fill(String s, String me, String partner) {
        if (s == null) return "";
        return s.replace("{{partner}}", partner == null ? "" : partner).replace("{{me}}", me == null ? "" : me);
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStrings(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : Json.read(json, List.class)) if (o != null) out.add(o.toString());
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readMaps(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : Json.read(json, List.class)) if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        return out;
    }

    private record Copy(String headline, List<String> blurb, List<Map<String, Object>> reasons,
                        List<String> openers, String source) {}
}
