package cn.aiditto.domain.matchlab;

import cn.aiditto.domain.matchlab.LabWorkbench.LabRejection;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One in-memory workspace per admin. A restart drops every workspace. This is not a distributed sandbox.
 */
public final class WorkspaceBoard {
    public static final int MAX_WORKSPACES = 32;
    public static final Duration IDLE = Duration.ofHours(2);
    public static final String STORAGE = "MEMORY_ONLY_LOST_ON_RESTART";

    private final Map<String, Workspace> byId = new LinkedHashMap<>();
    private final Map<Long, String> byOwner = new LinkedHashMap<>();

    public synchronized Workspace resolve(long ownerPrincipalId, String requestedId) {
        evict();
        String owned = byOwner.get(ownerPrincipalId);
        if (requestedId != null && !requestedId.isBlank() && (owned == null || !owned.equals(requestedId))) {
            throw new LabRejection("WORKSPACE");
        }
        if (owned != null) {
            Workspace current = byId.get(owned);
            if (current != null && current.expiresAt().isAfter(Instant.now())) {
                Workspace touched = current.touch();
                byId.put(owned, touched);
                return touched;
            }
        }
        return create(ownerPrincipalId);
    }

    public synchronized Workspace reset(long ownerPrincipalId, String workspaceId) {
        Workspace current = resolve(ownerPrincipalId, workspaceId);
        byId.remove(current.workspaceId());
        byOwner.remove(ownerPrincipalId);
        return create(ownerPrincipalId);
    }

    public synchronized int size() { return byId.size(); }

    private Workspace create(long ownerPrincipalId) {
        evict();
        while (byId.size() >= MAX_WORKSPACES) {
            Iterator<String> ids = byId.keySet().iterator();
            String oldest = ids.next();
            Workspace removed = byId.remove(oldest);
            if (removed != null) byOwner.remove(removed.ownerPrincipalId());
        }
        String id = "ws-" + UUID.randomUUID();
        LabWorkbench lab = new LabWorkbench();
        lab.bindWorkspace(id);
        Instant now = Instant.now();
        Workspace created = new Workspace(id, ownerPrincipalId, LabWorkbench.MODE, now, now, 1, lab, now.plus(IDLE));
        byId.put(id, created);
        byOwner.put(ownerPrincipalId, id);
        return created;
    }

    private void evict() {
        Instant now = Instant.now();
        Iterator<Workspace> spaces = byId.values().iterator();
        while (spaces.hasNext()) {
            Workspace space = spaces.next();
            if (!space.expiresAt().isAfter(now)) {
                spaces.remove();
                byOwner.remove(space.ownerPrincipalId(), space.workspaceId());
            }
        }
    }

    public record Workspace(String workspaceId, long ownerPrincipalId, String mode, Instant createdAt,
                            Instant lastAccessAt, long revision, LabWorkbench workbench, Instant expiresAt) {
        private Workspace touch() {
            Instant now = Instant.now();
            return new Workspace(workspaceId, ownerPrincipalId, mode, createdAt, now, revision + 1, workbench, now.plus(IDLE));
        }
    }
}
