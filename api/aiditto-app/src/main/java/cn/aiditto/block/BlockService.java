package cn.aiditto.block;

import cn.aiditto.account.UserLock;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.questionnaire.AnswerValidator;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.crypto.Pii;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BlockService {
    public static final int MAX_ENTRIES = 20;
    static final int MAX_CONTACTS = 10;
    static final int NAME_MAX = 64;
    static final int NOTE_MAX = 200;

    /** Same shape as {@code BlockEntry} in web/src/api/types.ts. */
    public record BlockView(String id, String name, List<String> emails, List<String> phones, String note, String createdAt) {}

    public record NewBlock(String name, List<String> emails, List<String> phones, String note) {}

    private final BlockMapper blocks;
    private final UserLock userLock;
    private final Crypto crypto;
    private final Clock clock;

    public BlockService(BlockMapper blocks, UserLock userLock, Crypto crypto, Clock clock) {
        this.blocks = blocks;
        this.userLock = userLock;
        this.crypto = crypto;
        this.clock = clock;
    }

    public List<BlockView> list(long userId) {
        return blocks.selectList(new QueryWrapper<Block>().eq("user_id", userId).orderByDesc("created_at").orderByDesc("id"))
                .stream().map(this::view).toList();
    }

    @Transactional
    public BlockView add(long userId, NewBlock in) {
        String name = trim(in.name(), NAME_MAX, "name");
        String note = trim(in.note(), NOTE_MAX, "note");
        List<String> emails = emails(in.emails());
        List<String> phones = phones(in.phones());
        if (emails.isEmpty() && phones.isEmpty()) throw new DomainException(ErrorCode.BLOCK_NEEDS_CONTACT, null, List.of("emails", "phones"), null);

        userLock.lock(userId);
        if (blocks.selectCount(new QueryWrapper<Block>().eq("user_id", userId)) >= MAX_ENTRIES) throw new DomainException(ErrorCode.BLOCK_LIMIT);
        Block b = new Block();
        b.userId = userId;
        b.nameEnc = crypto.encrypt(name);
        b.emailsEnc = crypto.encrypt(Json.write(emails));
        b.phonesEnc = crypto.encrypt(Json.write(phones));
        b.noteEnc = crypto.encrypt(note);
        b.createdAt = Times.millis(clock.instant());
        blocks.insert(b);
        for (String e : emails) blocks.insertContact(b.id, userId, "email", crypto.hash(e));
        for (String p : phones) blocks.insertContact(b.id, userId, "phone", crypto.hash(p));
        return view(b);
    }

    @Transactional
    public void delete(long userId, long id) {
        Block b = blocks.selectById(id);
        if (b == null || b.userId != userId) throw new DomainException(ErrorCode.NOT_FOUND);
        blocks.deleteContacts(id);
        blocks.deleteById(id);
    }

    private BlockView view(Block b) {
        return new BlockView(String.valueOf(b.id), crypto.decrypt(b.nameEnc), Json.readStringList(crypto.decrypt(b.emailsEnc)),
                Json.readStringList(crypto.decrypt(b.phonesEnc)), crypto.decrypt(b.noteEnc), Times.iso(b.createdAt));
    }

    private static String trim(String s, int max, String field) {
        String v = s == null ? "" : s.strip();
        if (v.length() > max) throw DomainException.invalid(field, "最多 " + max + " 个字");
        return v;
    }

    private static List<String> emails(List<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String r : raw == null ? List.<String>of() : raw) {
            String e = Pii.normalizeEmail(r);
            if (e.isEmpty()) continue;
            if (!AnswerValidator.isEmail(e)) throw DomainException.invalid("emails", "邮箱格式不对：" + r);
            out.add(e);
        }
        if (out.size() > MAX_CONTACTS) throw DomainException.invalid("emails", "最多 " + MAX_CONTACTS + " 个邮箱");
        return List.copyOf(out);
    }

    private static List<String> phones(List<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String r : raw == null ? List.<String>of() : raw) {
            if (r == null || r.isBlank()) continue;
            String p = Pii.normalizePhone(r);
            // The English UI accepts international numbers (BlocklistPage validPhone: ≥ 7 digits).
            if (!Pii.isCnMobile(p) && (p.length() < 7 || p.length() > 15)) throw DomainException.invalid("phones", "手机号格式不对：" + r);
            out.add(p);
        }
        if (out.size() > MAX_CONTACTS) throw DomainException.invalid("phones", "最多 " + MAX_CONTACTS + " 个手机号");
        return List.copyOf(out);
    }
}
