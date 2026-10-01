package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.UUID;

public final class EntityChange {
    public enum Kind {
        CREATE,
        DELETE
    }

    private final Kind kind;
    private final ClipboardEntity entity;
    private final Location location;
    private UUID liveEntityId;

    private EntityChange(Kind kind, ClipboardEntity entity, Location location, UUID liveEntityId) {
        this.kind = kind;
        this.entity = entity;
        this.location = location;
        this.liveEntityId = liveEntityId;
    }

    public static EntityChange created(ClipboardEntity entity, Location location, Entity liveEntity) {
        return new EntityChange(Kind.CREATE, entity, location.clone(), liveEntity == null ? null : liveEntity.getUniqueId());
    }

    public static EntityChange deleted(ClipboardEntity entity, Location location) {
        return new EntityChange(Kind.DELETE, entity, location.clone(), null);
    }

    public static EntityChange restored(Kind kind, ClipboardEntity entity, Location location, UUID liveEntityId) {
        return new EntityChange(kind, entity, location.clone(), liveEntityId);
    }

    public void undo() {
        if (kind == Kind.CREATE) {
            removeLiveEntity();
            return;
        }
        Entity recreated = entity.spawn(requireWorld(), location);
        liveEntityId = recreated.getUniqueId();
    }

    public void redo() {
        if (kind == Kind.CREATE) {
            Entity recreated = entity.spawn(requireWorld(), location);
            liveEntityId = recreated.getUniqueId();
            return;
        }
        removeLiveEntity();
    }

    private void removeLiveEntity() {
        if (liveEntityId == null) {
            return;
        }
        Entity existing = Bukkit.getEntity(liveEntityId);
        if (existing != null) {
            existing.remove();
        }
        liveEntityId = null;
    }

    private World requireWorld() {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("Entity change world is unavailable.");
        }
        return world;
    }

    public Kind getKind() {
        return kind;
    }

    public ClipboardEntity getEntity() {
        return entity;
    }

    public Location getLocation() {
        return location.clone();
    }

    public UUID getLiveEntityId() {
        return liveEntityId;
    }
}
