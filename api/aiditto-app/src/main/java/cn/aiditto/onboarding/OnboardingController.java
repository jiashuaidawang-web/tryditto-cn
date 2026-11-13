package cn.aiditto.onboarding;

import cn.aiditto.auth.AuthUser;
import cn.aiditto.infra.json.Json;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {
    private final OnboardingService onboarding;

    public OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    /** Body {@code { "value": <any>, "furthestStepId": "hobbies"? }}; a null or missing value deletes the answer. */
    @PutMapping("/answers/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void answer(@AuthenticationPrincipal AuthUser me, @PathVariable String questionId, @RequestBody JsonNode body) {
        onboarding.writeAnswer(me.userId(), questionId, Json.toPlain(body.get("value")));
        JsonNode step = body.get("furthestStepId");
        if (step != null && step.isString()) onboarding.setFurthestStep(me.userId(), step.asString());
    }

    record ProgressRequest(String furthestStepId) {}

    /** For steps that write no answer (interstitials, photos). */
    @PutMapping("/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void progress(@AuthenticationPrincipal AuthUser me, @RequestBody ProgressRequest body) {
        onboarding.setFurthestStep(me.userId(), body.furthestStepId());
    }

    @GetMapping("/progress")
    OnboardingService.Progress progress(@AuthenticationPrincipal AuthUser me) {
        return onboarding.progress(me.userId());
    }

    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void complete(@AuthenticationPrincipal AuthUser me) {
        onboarding.complete(me.userId());
    }
}
