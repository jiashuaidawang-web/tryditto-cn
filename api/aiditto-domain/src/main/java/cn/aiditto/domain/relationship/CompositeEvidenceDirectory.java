package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.EvidenceSourceDirectory.SourceFact;

import java.util.List;
import java.util.Optional;

/** Asks each directory in order. A miss in one directory is not a miss in the next. */
public final class CompositeEvidenceDirectory implements EvidenceSourceDirectory {
    private final List<EvidenceSourceDirectory> parts;

    public CompositeEvidenceDirectory(EvidenceSourceDirectory... parts) {
        this.parts = List.of(parts);
    }

    @Override
    public Optional<SourceFact> find(long userId, String sourceRef, String sourceVersion) {
        for (EvidenceSourceDirectory part : parts) {
            Optional<SourceFact> found = part.find(userId, sourceRef, sourceVersion);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }
}
