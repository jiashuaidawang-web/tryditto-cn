package cn.aiditto.it;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingIT extends ApiIT {
    static final String EMAIL = "xiaoming@pku.edu.cn";

    private void answerEverythingRequired(String token) {
        answer(token, "isStudent", "yes");
        answer(token, "name", "小明");
        answer(token, "gender", "man");
        answer(token, "expectedGender", List.of("women"));
        answer(token, "birthday", "2004-05-01");
        answer(token, "hobbies", List.of("music", "reading"));
        answer(token, "musicSound", List.of("indie"));
        answer(token, "readingGenres", List.of("scifi"));
        answer(token, "location", Map.of("placeId", "wudaokou", "mode", "transit", "minutes", 45));
        answer(token, "relationshipLooking", List.of("serious"));
        answer(token, "matchingStyle", "steady");
        answer(token, "schoolYear", "junior");
        answer(token, "schoolEmail", EMAIL);
        answer(token, "religion", List.of("buddhist"));
    }

    @Test
    void writeThroughSurvivesANewSession() {
        String phone = randomPhone();
        String token = login(phone);
        answerEverythingRequired(token);

        Resp early = call("POST", "/api/onboarding/complete", null, token);
        assertThat(early.status()).isEqualTo(422);
        assertThat(early.code()).isEqualTo("ONBOARDING_INCOMPLETE");
        assertThat(JSON.convertValue(early.body().get("fields"), List.class)).containsExactly("photos", "emailVerify");

        Resp notVerified = call("PUT", "/api/onboarding/answers/emailVerify", Map.of("value", "888888"), token);
        assertThat(notVerified.status()).isEqualTo(409);
        assertThat(notVerified.code()).isEqualTo("EMAIL_NOT_VERIFIED");

        Resp sent = call("POST", "/api/auth/email/code", Map.of("email", EMAIL), token);
        assertThat(sent.body().get("sentTo").asString()).isEqualTo("x***@pku.edu.cn");
        assertThat(call("POST", "/api/auth/email/verify", Map.of("email", EMAIL, "code", "111111"), token).body().get("verified").asBoolean()).isFalse();
        assertThat(call("POST", "/api/auth/email/verify", Map.of("email", EMAIL, "code", DEMO_CODE), token).body().get("verified").asBoolean()).isTrue();
        answer(token, "emailVerify", DEMO_CODE);

        uploadPhoto(token, "profile", 800, 1000);
        uploadPhoto(token, "profile", 1000, 800);
        Resp progressStep = call("PUT", "/api/onboarding/answers/preferencesIntro", Map.of("value", true), token);
        assertThat(progressStep.status()).isEqualTo(422);
        assertThat(call("PUT", "/api/onboarding/progress", Map.of("furthestStepId", "relationshipLooking"), token).status()).isEqualTo(204);

        assertThat(call("POST", "/api/onboarding/complete", null, token).status()).isEqualTo(204);

        // Another browser: a brand-new session sees the same data.
        String other = login(phone);
        assertThat(other).isNotEqualTo(token);
        JsonNode progress = call("GET", "/api/onboarding/progress", null, other).body();
        assertThat(progress.get("completed").asBoolean()).isTrue();
        assertThat(progress.get("furthestStepId").asString()).isEqualTo("relationshipLooking");
        JsonNode answers = progress.get("answers");
        assertThat(answers.get("name").asString()).isEqualTo("小明");
        assertThat(answers.get("photos").asInt()).isEqualTo(2);
        assertThat(answers.get("location").get("minutes").asInt()).isEqualTo(45);
        assertThat(answers.get("expectedGender").get(0).asString()).isEqualTo("women");
        assertThat(answers.has("emailVerify")).isFalse();

        JsonNode profile = call("GET", "/api/profile", null, other).body();
        assertThat(profile.get("photos").size()).isEqualTo(2);
        assertThat(profile.get("photos").get(0).get("url").asString()).startsWith("/api/photos/");

        JsonNode me = call("GET", "/api/me", null, other).body();
        assertThat(me.get("onboarded").asBoolean()).isTrue();
        assertThat(me.get("status").asString()).isEqualTo("active");
        assertThat(me.get("name").asString()).isEqualTo("小明");
        assertThat(me.get("email").asString()).isEqualTo("x***@pku.edu.cn");

        long userId = Long.parseLong(me.get("id").asString());
        Map<String, Object> row = jdbc.queryForMap("SELECT value_json, value_enc FROM onboarding_answer WHERE user_id = ? AND question_id = 'religion'", userId);
        assertThat(row.get("value_json")).isNull();
        assertThat(row.get("value_enc")).isNotNull();
        Map<String, Object> p = jdbc.queryForMap("SELECT version, place_id, travel_minutes, photo_count, school_domain, age_min, age_max FROM profile WHERE user_id = ?", userId);
        assertThat(p).containsEntry("version", 1).containsEntry("place_id", "wudaokou").containsEntry("travel_minutes", 45)
                .containsEntry("photo_count", 2).containsEntry("school_domain", "pku.edu.cn").containsEntry("age_min", 18).containsEntry("age_max", 24);
    }

    @Test
    void underageBirthdayIsRejected() {
        String token = login(randomPhone());
        LocalDate seventeen = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusYears(17);
        Resp r = call("PUT", "/api/onboarding/answers/birthday", Map.of("value", seventeen.toString()), token);
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.code()).isEqualTo("UNDER_AGE");
        assertThat(JSON.convertValue(r.body().get("fields"), List.class)).containsExactly("birthday");
        assertThat(call("GET", "/api/onboarding/progress", null, token).body().get("answers").has("birthday")).isFalse();
    }

    @Test
    void invalidAnswersAreRejected() {
        String token = login(randomPhone());
        assertThat(call("PUT", "/api/onboarding/answers/gender", Map.of("value", "robot"), token).code()).isEqualTo("VALIDATION");
        assertThat(call("PUT", "/api/onboarding/answers/hobbies", Map.of("value", List.of("music", "travel", "art", "gym", "reading", "movies")), token).code())
                .isEqualTo("VALIDATION");
        assertThat(call("PUT", "/api/onboarding/answers/schoolEmail", Map.of("value", "me@gmail.com"), token).code()).isEqualTo("INVALID_EMAIL");
        assertThat(call("PUT", "/api/onboarding/answers/location", Map.of("value", Map.of("placeId", "mars", "mode", "walk", "minutes", 30)), token).code())
                .isEqualTo("VALIDATION");
        assertThat(call("PUT", "/api/onboarding/answers/nope", Map.of("value", "x"), token).status()).isEqualTo(404);
    }

    @Test
    void hobbyChangeDropsStaleBranchesAndNullDeletes() {
        String token = login(randomPhone());
        answer(token, "hobbies", List.of("music", "travel"));
        answer(token, "musicSound", List.of("pop"));
        answer(token, "travelStyle", List.of("beach"));
        answer(token, "hobbies", List.of("music"));
        answer(token, "occupation", "工程师");
        answer(token, "occupation", null);
        answer(token, "noSchoolEmail", true);
        JsonNode answers = call("GET", "/api/onboarding/progress", null, token).body().get("answers");
        assertThat(answers.has("musicSound")).isTrue();
        assertThat(answers.has("travelStyle")).isFalse();
        assertThat(answers.has("occupation")).isFalse();
        assertThat(answers.get("noSchoolEmail").asBoolean()).isTrue();
    }

    @Test
    void profileEditsAfterCompletion() {
        String phone = randomPhone();
        String token = login(phone);
        answerEverythingRequired(token);
        call("POST", "/api/auth/email/verify", Map.of("email", EMAIL, "code", DEMO_CODE), token);
        uploadPhoto(token, "profile", 600, 600);
        uploadPhoto(token, "profile", 600, 600);
        assertThat(call("POST", "/api/onboarding/complete", null, token).status()).isEqualTo(204);

        assertThat(call("PUT", "/api/profile/ageRange", Map.of("value", List.of(20, 28)), token).status()).isEqualTo(204);
        assertThat(call("PUT", "/api/profile/location", Map.of("placeId", "sanlitun", "mode", "drive", "minutes", 30), token).status()).isEqualTo(204);
        Resp locked = call("PUT", "/api/profile/birthday", Map.of("value", "2003-01-01"), token);
        assertThat(locked.status()).isEqualTo(409);
        assertThat(locked.code()).isEqualTo("FIELD_LOCKED");
        assertThat(call("PUT", "/api/profile/birthday", Map.of("value", "2004-05-01"), token).status()).isEqualTo(204);
        assertThat(call("PUT", "/api/profile/name", java.util.Collections.singletonMap("value", null), token).status()).isEqualTo(422);

        JsonNode answers = call("GET", "/api/onboarding/progress", null, token).body().get("answers");
        assertThat(answers.get("expectedAgeRange").get(1).asInt()).isEqualTo(28);
        assertThat(answers.get("location").get("placeId").asString()).isEqualTo("sanlitun");
        long userId = Long.parseLong(call("GET", "/api/me", null, token).body().get("id").asString());
        Map<String, Object> p = jdbc.queryForMap("SELECT version, age_max, place_id FROM profile WHERE user_id = ?", userId);
        assertThat((Integer) p.get("version")).isGreaterThanOrEqualTo(3);
        assertThat(p).containsEntry("age_max", 28).containsEntry("place_id", "sanlitun");

        JsonNode photos = call("GET", "/api/photos?kind=profile", null, token).body();
        Resp min = call("DELETE", "/api/photos/" + photos.get(0).get("id").asString(), null, token);
        assertThat(min.status()).isEqualTo(409);
        assertThat(min.code()).isEqualTo("PHOTO_MIN");
    }
}
