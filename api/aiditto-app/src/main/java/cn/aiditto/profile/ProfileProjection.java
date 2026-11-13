package cn.aiditto.profile;

import cn.aiditto.domain.questionnaire.ProfileProjector;
import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

/** Writes the {@code profile} row from the answers. Called at completion and after every later edit. */
@Component
public class ProfileProjection {
    private final ProfileMapper profiles;
    private final QuestionCatalog catalog;
    private final Crypto crypto;
    private final Clock clock;

    public ProfileProjection(ProfileMapper profiles, QuestionCatalog catalog, Crypto crypto, Clock clock) {
        this.profiles = profiles;
        this.catalog = catalog;
        this.crypto = crypto;
        this.clock = clock;
    }

    public void write(long userId, Map<String, Object> answers, long photoCount) {
        ProfileProjector.ProfileSnapshot s = ProfileProjector.project(answers, catalog);
        Profile existing = profiles.selectById(userId);
        Profile p = new Profile();
        p.userId = userId;
        p.version = existing == null ? 1 : existing.version + 1;
        p.name = s.name();
        p.gender = s.gender();
        p.expectedGendersEnc = crypto.encrypt(Json.write(s.expectedGenders()));
        p.birthday = s.birthday();
        p.ageMin = s.ageMin();
        p.ageMax = s.ageMax();
        p.heightCm = s.heightCm();
        p.heightAny = s.heightAny();
        p.heightMin = s.heightMin();
        p.heightMax = s.heightMax();
        p.isStudent = s.isStudent();
        p.schoolYear = s.schoolYear();
        p.schoolDomain = s.schoolDomain();
        p.occupation = s.occupation();
        p.educationLevel = s.educationLevel();
        p.educationImportance = s.educationImportance();
        p.budget = s.budget();
        p.matchingStyle = s.matchingStyle();
        p.relationshipLooking = Json.write(s.relationshipLooking());
        p.hobbies = Json.write(s.hobbies());
        p.hobbyDetails = Json.write(s.hobbyDetails());
        p.placeId = s.placeId();
        p.travelMode = s.travelMode();
        p.travelMinutes = s.travelMinutes();
        p.firstMessage = s.firstMessage();
        p.photoCount = (int) photoCount;
        p.updatedAt = Times.millis(clock.instant());
        if (existing == null) profiles.insert(p);
        else profiles.updateById(p);
    }
}
