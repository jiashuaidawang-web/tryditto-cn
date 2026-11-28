package cn.aiditto.relationship;

import cn.aiditto.account.AccountService;
import cn.aiditto.account.User;
import cn.aiditto.auth.AuthUser;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.integration.ProductIntegrationSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolated read of the signed-in user's V4 snapshot. It returns codes and versions, not answers,
 * contact details, or photos. Production and legacy mode answer 404.
 */
@RestController
@RequestMapping("/api/internal/profile-diagnostic")
public class ProfileDiagnosticController {
    private final ProductIntegrationSettings settings;
    private final RelationshipProfileService profiles;
    private final IsolatedProfileSync sync;
    private final AccountService accounts;

    public ProfileDiagnosticController(ProductIntegrationSettings settings, RelationshipProfileService profiles,
                                       IsolatedProfileSync sync, AccountService accounts) {
        this.settings = settings;
        this.profiles = profiles;
        this.sync = sync;
        this.accounts = accounts;
    }

    @GetMapping
    ResponseEntity<Map<String, Object>> read(@AuthenticationPrincipal AuthUser user) {
        if (!settings.isolatedTest()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(view(user.userId(), false));
    }

    /** No product screen grants processing. This entry is closed outside the isolated test mode. */
    @PostMapping("/grant")
    ResponseEntity<Map<String, Object>> grant(@AuthenticationPrincipal AuthUser user) {
        if (!settings.isolatedTest()) return ResponseEntity.notFound().build();
        User account = accounts.require(user.userId());
        if (!account.onboarded()) throw new DomainException(ErrorCode.ONBOARDING_INCOMPLETE, "入驻还没有完成");
        profiles.grantProcessing(user.userId());
        sync.projectStored(user.userId());
        return ResponseEntity.ok(view(user.userId(), true));
    }

    private Map<String, Object> view(long userId, boolean grantedHere) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grantedHere", grantedHere);
        User account = accounts.require(userId);
        if (User.DELETING.equals(account.status) || "purged".equals(account.status)) {
            body.put("usableForV4", false);
            body.put("reason", "ACCOUNT_INACTIVE");
            return body;
        }
        body.put("profileVersion", profiles.storedSourceVersion(userId));
        if (!profiles.processingOpen(userId)) {
            body.put("usableForV4", false);
            body.put("reason", "PROFILE_PROCESSING_NOT_GRANTED");
            body.put("featureCodes", List.of());
            return body;
        }
        var current = profiles.current(userId);
        if (current.isEmpty()) {
            body.put("usableForV4", false);
            body.put("reason", "PROFILE_UNAVAILABLE");
            body.put("generation", profiles.storedGeneration(userId));
            body.put("featureCodes", List.of());
            return body;
        }
        RelationshipProfileSnapshot projected = profiles.projectAuthoritative(userId);
        boolean fresh = projected.inputHash().equals(current.get().inputHash());
        body.put("usableForV4", fresh);
        body.put("reason", fresh ? "CURRENT" : "STALE_PROFILE");
        body.put("snapshotVersion", current.get().version());
        body.put("generation", profiles.storedGeneration(userId));
        body.put("inputHash", current.get().inputHash());
        body.put("featureCodes", fresh ? codes(projected) : List.of());
        return body;
    }

    private static List<String> codes(RelationshipProfileSnapshot snapshot) {
        List<String> codes = new ArrayList<>();
        for (ProfileFeature feature : snapshot.self().features()) codes.add(feature.featureCode());
        for (PreferenceTarget target : snapshot.preference().targets()) codes.add(target.featureCode());
        return List.copyOf(codes);
    }
}
