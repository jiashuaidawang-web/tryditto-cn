package cn.aiditto.relationship;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.relationship.ExtractedFeature;
import cn.aiditto.domain.relationship.ProfileContext;
import cn.aiditto.domain.relationship.ProfileContextBuilder;
import cn.aiditto.domain.relationship.ProfileExtractionPrompts;
import cn.aiditto.relationship.ExtractionTaskStore.Attempt;
import cn.aiditto.relationship.ExtractionTaskStore.Claim;
import cn.aiditto.relationship.RelationshipProfileService.ExtractionPermit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads authorization, calls the model outside any user-row lock, then asks the profile service
 * to recheck the generation, the export epoch, and the fence before writing.
 * Caller statements are not an input. The same model response cannot authorize its own write.
 *
 * The send decision is the permit read immediately before {@code gateway.call}. A withdrawal that
 * happens after that call has started cannot unsend bytes already given to the provider. The later
 * commit still refuses the write.
 */
@Service
public class RelationshipProfileExtractor {
    public record ExtractionRun(String status, String inputHash, String promptVersion, String model,
                                Integer inputTokens, Integer outputTokens, long latencyMs, int accepted) {}

    private final RelationshipProfileService profiles;
    private final ProfileInputLoader loader;
    private final ExtractionTaskStore tasks;
    private final LlmGateway gateway;
    private final AtomicReference<Runnable> beforeSend = new AtomicReference<>();

    public RelationshipProfileExtractor(RelationshipProfileService profiles, ProfileInputLoader loader,
                                        ExtractionTaskStore tasks, LlmGateway gateway) {
        this.profiles = profiles;
        this.loader = loader;
        this.tasks = tasks;
        this.gateway = gateway;
    }

    /** Test hook. It runs after the task claim and before the permit is read again for sending. */
    public void beforeSend(Runnable hook) {
        beforeSend.set(hook);
    }

    public ExtractionRun extract(long userId) {
        ExtractionPermit permit = profiles.readPermit(userId);
        if (!permit.processingGranted()) {
            throw new DomainException(ErrorCode.FORBIDDEN, "没有画像处理授权，不能构建画像");
        }
        if (permit.sourceVersion() < 0) {
            throw DomainException.invalid("sourceProfileVersion", "没有可核对的源版本");
        }
        ProfileContext context = ProfileContextBuilder.build(loader.load(userId, permit.sourceVersion(),
                permit.generation(), true, permit.modelExportGranted(), permit.modelExportEpoch()));
        if (!context.modelExportGranted()) {
            return run("export-denied", context, null, 0);
        }
        Claim claim = tasks.tryStart(userId, context.inputHash(), context.promptVersion(),
                context.generationEpoch(), permit.modelExportEpoch());
        if (claim.attempt() == Attempt.DONE) return run("skipped", context, null, 0);
        if (claim.attempt() == Attempt.BUSY) return run("busy", context, null, 0);
        if (claim.attempt() == Attempt.EXHAUSTED) return run("exhausted", context, null, 0);
        Runnable hook = beforeSend.getAndSet(null);
        if (hook != null) hook.run();
        ExtractionPermit current;
        try {
            current = profiles.readPermit(userId);
        } catch (DomainException inactive) {
            tasks.discard(userId, context.inputHash(), context.promptVersion(), context.generationEpoch(), claim.fence());
            throw inactive;
        }
        if (!current.modelExportGranted() || current.modelExportEpoch() != permit.modelExportEpoch()
                || current.generation() != permit.generation() || current.sourceVersion() != permit.sourceVersion()) {
            tasks.discard(userId, context.inputHash(), context.promptVersion(), context.generationEpoch(), claim.fence());
            return run("discarded", context, null, 0);
        }
        LlmResult result;
        try {
            result = gateway.call(new LlmRequest(ProfileExtractionPrompts.CALL_POINT, context.promptVersion(),
                    ProfileExtractionPrompts.system(), ProfileExtractionPrompts.userPayload(context.exportCanonical()),
                    1200, 0.0, true, true));
        } catch (RuntimeException failed) {
            tasks.degrade(userId, context.inputHash(), context.promptVersion(), context.generationEpoch(), claim.fence());
            throw failed;
        }
        if (!result.ok()) {
            tasks.degrade(userId, context.inputHash(), context.promptVersion(), context.generationEpoch(), claim.fence());
            return run("degraded", context, result, 0);
        }
        ProfileExtractionParser.Parse parsed = ProfileExtractionParser.parse(result.content());
        if (!parsed.structured()) {
            tasks.degrade(userId, context.inputHash(), context.promptVersion(), context.generationEpoch(), claim.fence());
            return run("degraded", context, result, 0);
        }
        try {
            return finish(userId, context, claim.fence(), parsed.features(), result);
        } catch (DomainException e) {
            tasks.discard(userId, context.inputHash(), context.promptVersion(), context.generationEpoch(), claim.fence());
            return run("discarded", context, result, 0);
        } catch (RuntimeException e) {
            tasks.discard(userId, context.inputHash(), context.promptVersion(), context.generationEpoch(), claim.fence());
            throw e;
        }
    }

    public ExtractionRun finish(long userId, ProfileContext context, long fence,
                                List<ExtractedFeature> proposed, LlmResult result) {
        RelationshipProfileService.ProvisionalWrite written = profiles.commitProvisional(
                userId, context.generationEpoch(), context.modelExportEpoch(), context.inputHash(), fence, proposed);
        return run("completed", context, result, written.accepted());
    }

    private static ExtractionRun run(String status, ProfileContext context, LlmResult result, int accepted) {
        return new ExtractionRun(status, context.inputHash(), context.promptVersion(),
                result == null ? null : result.model(),
                result == null ? null : result.inputTokens(),
                result == null ? null : result.outputTokens(),
                result == null ? 0 : result.latencyMs(), accepted);
    }
}
