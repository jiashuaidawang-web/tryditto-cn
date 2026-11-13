package cn.aiditto.profile;

import cn.aiditto.auth.AuthUser;
import cn.aiditto.domain.questionnaire.FieldAliases;
import cn.aiditto.infra.json.Json;
import cn.aiditto.onboarding.OnboardingService;
import cn.aiditto.photo.PhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final OnboardingService onboarding;
    private final PhotoService photos;

    public ProfileController(OnboardingService onboarding, PhotoService photos) {
        this.onboarding = onboarding;
        this.photos = photos;
    }

    public record ProfileView(Map<String, Object> answers, List<PhotoService.PhotoView> photos,
                              List<PhotoService.PhotoView> idealPhotos, String furthestStepId, boolean completed) {}

    /** Everything the profile pages need in one call; the frontend hydrates its cache from it after login. */
    @GetMapping
    ProfileView get(@AuthenticationPrincipal AuthUser me) {
        OnboardingService.Progress p = onboarding.progress(me.userId());
        return new ProfileView(p.answers(), photos.list(me.userId(), "profile"), photos.list(me.userId(), "ideal"),
                p.furthestStepId(), p.completed());
    }

    @PutMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void location(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("placeId", Json.toPlain(body.get("placeId")));
        value.put("mode", Json.toPlain(body.get("mode")));
        value.put("minutes", Json.toPlain(body.get("minutes")));
        onboarding.writeAnswer(me.userId(), "location", value);
    }

    /** {@code field} is a question id or a Ditto field name (技术方案 §4.4 aliases). */
    @PutMapping("/{field}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void field(@AuthenticationPrincipal AuthUser me, @PathVariable String field, @RequestBody JsonNode body) {
        onboarding.writeAnswer(me.userId(), FieldAliases.toQuestionId(field), Json.toPlain(body.get("value")));
    }
}
