package cn.aiditto.consent;

import cn.aiditto.auth.AuthUser;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.infra.web.Times;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** The caller's own matching permission. Nothing here describes another person. */
@RestController
public class MatchingConsentController {
    private final MatchingConsentService consents;

    public MatchingConsentController(MatchingConsentService consents) {
        this.consents = consents;
    }

    @GetMapping("/api/me/matching-consent")
    Map<String, Object> current(@AuthenticationPrincipal AuthUser user) {
        return view(consents.current(user.userId()));
    }

    @PostMapping("/api/me/matching-consent")
    @ResponseStatus(HttpStatus.OK)
    Map<String, Object> grant(@AuthenticationPrincipal AuthUser user) {
        return view(consents.grant(user.userId()));
    }

    @DeleteMapping("/api/me/matching-consent")
    Map<String, Object> revoke(@AuthenticationPrincipal AuthUser user) {
        return view(consents.revoke(user.userId()));
    }

    static Map<String, Object> view(MatchingConsent consent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consentStatus", consent.consentStatus().name());
        body.put("consentVersion", consent.consentVersion());
        body.put("grantedAt", consent.grantedAt() == null ? null : Times.iso(consent.grantedAt()));
        body.put("revokedAt", consent.revokedAt() == null ? null : Times.iso(consent.revokedAt()));
        body.put("policyVersion", consent.policyVersion());
        body.put("updatedAt", consent.updatedAt() == null ? null : Times.iso(consent.updatedAt()));
        return body;
    }
}
