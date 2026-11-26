package cn.aiditto.relationship;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.relationship.ExtractedFeature;
import cn.aiditto.domain.relationship.ProfileContext;
import cn.aiditto.domain.relationship.ProfileContextBuilder;
import cn.aiditto.domain.relationship.ProfileContextBuilder.Statement;
import cn.aiditto.domain.relationship.ProfileExtractionPrompts;
import cn.aiditto.relationship.ExtractionTaskStore.Attempt;
import cn.aiditto.relationship.RelationshipProfileService.ExtractionPermit;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads authorization, calls the model outside the user lock, then asks the profile service
 * to recheck the generation and write. Caller-built snapshots still cannot use saveModelRevision.
 */
@Service
public class RelationshipProfileExtractor {
    public record ExtractionRun(String status, String inputHash, String promptVersion, String model,
                                Integer inputTokens, Integer outputTokens, long latencyMs, int accepted) {}

    private final RelationshipProfileService profiles;
    private final ProfileInputLoader loader;
    private final ExtractionTaskStore tasks;
    private final LlmGateway gateway;

    public RelationshipProfileExtractor(RelationshipProfileService profiles, ProfileInputLoader loader,
                                        ExtractionTaskStore tasks, LlmGateway gateway) {
        this.profiles = profiles;
        this.loader = loader;
        this.tasks = tasks;
        this.gateway = gateway;
    }

    public ExtractionRun extract(long userId, List<Statement> statements) {
        List<Statement> supplied = statements == null ? List.of() : List.copyOf(statements);
        ExtractionPermit permit = profiles.readPermit(userId);
        if (!permit.processingGranted()) {
            throw new DomainException(ErrorCode.FORBIDDEN, "没有画像处理授权，不能构建画像");
        }
        if (permit.sourceVersion() < 0) {
            throw DomainException.invalid("sourceProfileVersion", "没有可核对的源版本");
        }
        ProfileContext context = ProfileContextBuilder.build(loader.load(userId, permit.sourceVersion(),
                permit.generation(), true, permit.modelExportGranted(), supplied));
        if (!context.modelExportGranted()) {
            return run("export-denied", context, null, 0);
        }
        Attempt attempt = tasks.tryStart(userId, context.inputHash(), context.promptVersion(), context.generationEpoch());
        if (attempt == Attempt.DONE) return run("skipped", context, null, 0);
        if (attempt == Attempt.BUSY) return run("busy", context, null, 0);
        if (attempt == Attempt.EXHAUSTED) return run("exhausted", context, null, 0);
        LlmResult result = gateway.call(new LlmRequest(ProfileExtractionPrompts.CALL_POINT, context.promptVersion(),
                ProfileExtractionPrompts.system(), ProfileExtractionPrompts.userPayload(context.exportCanonical()),
                1200, 0.0, true, true));
        if (!result.ok()) {
            tasks.degrade(userId, context.inputHash(), context.promptVersion(), context.generationEpoch());
            return run("degraded", context, result, 0);
        }
        ProfileExtractionParser.Parse parsed = ProfileExtractionParser.parse(result.content());
        if (!parsed.structured()) {
            tasks.degrade(userId, context.inputHash(), context.promptVersion(), context.generationEpoch());
            return run("degraded", context, result, 0);
        }
        try {
            return finish(userId, context, supplied, parsed.features(), result);
        } catch (DomainException e) {
            tasks.discard(userId, context.inputHash(), context.promptVersion(), context.generationEpoch());
            return run("discarded", context, result, 0);
        } catch (RuntimeException e) {
            tasks.discard(userId, context.inputHash(), context.promptVersion(), context.generationEpoch());
            throw e;
        }
    }

    public ExtractionRun finish(long userId, ProfileContext context, List<Statement> statements,
                                List<ExtractedFeature> proposed, LlmResult result) {
        RelationshipProfileService.ProvisionalWrite written = profiles.commitProvisional(
                userId, context.generationEpoch(), context.inputHash(), statements, proposed);
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
