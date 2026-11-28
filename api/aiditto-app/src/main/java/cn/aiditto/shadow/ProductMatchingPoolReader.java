package cn.aiditto.shadow;

import cn.aiditto.consent.MatchingConsentService;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.PoolGate;
import cn.aiditto.domain.matching.v4.PoolExclusion;
import cn.aiditto.domain.relationship.ProfileVersion;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.integration.ProductIntegrationSettings;
import cn.aiditto.match.People;
import cn.aiditto.relationship.RelationshipProfileService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads one city's product rows. Callers do not supply consent, versions, or profile bodies. */
@Component
public class ProductMatchingPoolReader {
    private final People people;
    private final MatchingConsentService consents;
    private final RelationshipProfileService profiles;
    private final ProductIntegrationSettings settings;
    private final JdbcTemplate jdbc;

    public ProductMatchingPoolReader(People people, MatchingConsentService consents, RelationshipProfileService profiles,
                                     ProductIntegrationSettings settings, JdbcTemplate jdbc) {
        this.people = people;
        this.consents = consents;
        this.profiles = profiles;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    public PoolRead read(String city, LocalDate period, Instant now, String cohort) {
        if (!settings.shadowTest()) throw new IllegalStateException("shadow pool is closed");
        List<People.Member> cityMembers = people.city(city, period, now);
        List<Eligible> eligible = new ArrayList<>();
        List<Exclusion> excluded = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (People.Member member : cityMembers) {
            seen.add(member.userId());
            PoolExclusion reason = exclude(member, cohort);
            if (reason == null) eligible.add(admit(member));
            else excluded.add(new Exclusion(member.userId(), reason));
        }
        jdbc.query("""
                SELECT u.id, u.status
                FROM "user" u
                JOIN profile p ON p.user_id = u.id
                JOIN place pl ON pl.id = p.place_id
                WHERE pl.city = ? AND u.status NOT IN ('active', 'paused', 'suspended')
                ORDER BY u.id
                """, rs -> {
            while (rs.next()) {
                long id = rs.getLong(1);
                if (!seen.add(id)) continue;
                String status = rs.getString(2);
                excluded.add(new Exclusion(id, "onboarding".equals(status)
                        ? PoolExclusion.ONBOARDING_INCOMPLETE : PoolExclusion.ACCOUNT_INACTIVE));
            }
            return null;
        }, city);
        return new PoolRead(List.copyOf(cityMembers), List.copyOf(eligible), List.copyOf(excluded));
    }

    private PoolExclusion exclude(People.Member member, String cohort) {
        if (member.excluded() != null) return map(member.excluded());
        if (member.candidate() == null || member.candidate().gender() == null || member.candidate().birthday() == null
                || member.candidate().pool() == null) {
            return PoolExclusion.INVALID_BUSINESS_FACTS;
        }
        if (cohort == null || !cohort.equals(member.cohort())) return PoolExclusion.OUTSIDE_COHORT;
        MatchingConsent consent = consents.current(member.userId());
        if (consent.consentStatus() == ConsentStatus.REVOKED) return PoolExclusion.MATCH_CONSENT_REVOKED;
        if (consent.consentStatus() != ConsentStatus.GRANTED) return PoolExclusion.MATCH_CONSENT_MISSING;
        if (!profiles.processingOpen(member.userId())) return PoolExclusion.PROFILE_PROCESSING_NOT_GRANTED;
        Optional<ProfileVersion> current = profiles.current(member.userId());
        if (current.isEmpty() || current.get().version() < 1) return PoolExclusion.PROFILE_UNAVAILABLE;
        ProfileVersion version = current.get();
        if (version.sourceProfileVersion() != member.profileVersion() || profiles.storedGeneration(member.userId()) < 1) {
            return PoolExclusion.STALE_PROFILE;
        }
        RelationshipProfileSnapshot projected = profiles.projectAuthoritative(member.userId());
        if (!projected.inputHash().equals(version.inputHash())) return PoolExclusion.STALE_PROFILE;
        return null;
    }

    private Eligible admit(People.Member member) {
        ProfileVersion version = profiles.current(member.userId()).orElseThrow();
        RelationshipProfileSnapshot snapshot = profiles.projectAuthoritative(member.userId());
        MatchingConsent consent = consents.current(member.userId());
        return new Eligible(member, snapshot, version, profiles.storedGeneration(member.userId()), consent.consentVersion());
    }

    private static PoolExclusion map(PoolGate.ExcludeReason reason) {
        return switch (reason) {
            case INCOMPLETE, PHOTOS -> PoolExclusion.ONBOARDING_INCOMPLETE;
            case UNDER_18 -> PoolExclusion.INVALID_BUSINESS_FACTS;
            case OPEN_MATCH -> PoolExclusion.OPEN_MATCH;
            case NOT_ACTIVE, PAUSED, SUSPENDED -> PoolExclusion.ACCOUNT_INACTIVE;
        };
    }

    public record Eligible(People.Member member, RelationshipProfileSnapshot snapshot, ProfileVersion version,
                           long generation, int consentVersion) {}

    public record Exclusion(long userId, PoolExclusion reason) {}

    public record PoolRead(List<People.Member> city, List<Eligible> eligible, List<Exclusion> excluded) {}
}
