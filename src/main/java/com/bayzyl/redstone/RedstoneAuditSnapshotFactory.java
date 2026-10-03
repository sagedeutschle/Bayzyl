package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.WireLink;
import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.Observer;
import org.bukkit.block.data.type.RedstoneWallTorch;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Repeater;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The only audit code that reads the world. It refuses selections whose read volume (the selection plus its
 * {@link RedstoneAuditSnapshot#HALO}-block ring, clamped to the world's height) is over the cap before reading
 * anything, then reads that volume and turns each block into an {@link AuditCell}. Production readers are wrapped in
 * {@link #loadedOnly} so unloaded chunks are never loaded or generated. Call on the main thread; it never changes a
 * block.
 */
public final class RedstoneAuditSnapshotFactory {

    /** Reads one block; returns null for air or unloaded space. */
    @FunctionalInterface
    public interface BlockReader {
        BlockData read(int x, int y, int z);
    }

    /** Whether a chunk is already in memory; must not load or generate it. */
    @FunctionalInterface
    public interface ChunkProbe {
        boolean isLoaded(int chunkX, int chunkZ);
    }

    /**
     * Either a snapshot or the reason the audit was refused. {@code worldId} is the world the snapshot was read from
     * (null when unknown); markers use it to stay out of other worlds.
     */
    public record Capture(RedstoneAuditSnapshot snapshot, String refusal, UUID worldId) {
        public static Capture of(RedstoneAuditSnapshot snapshot) {
            return new Capture(snapshot, null, null);
        }

        public static Capture of(RedstoneAuditSnapshot snapshot, UUID worldId) {
            return new Capture(snapshot, null, worldId);
        }

        public static Capture refused(String reason) {
            return new Capture(null, reason, null);
        }
    }

    /**
     * Wraps {@code reader} so blocks in chunks that are not loaded read as null (air) without touching the chunk.
     * Each chunk is probed once.
     */
    public static BlockReader loadedOnly(ChunkProbe probe, BlockReader reader) {
        Map<Long, Boolean> loaded = new HashMap<>();
        return (x, y, z) -> {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            boolean isLoaded = loaded.computeIfAbsent(key, k -> probe.isLoaded(chunkX, chunkZ));
            return isLoaded ? reader.read(x, y, z) : null;
        };
    }

    private static final Set<String> FULL_SOURCES = Set.of("LEVER", "REDSTONE_BLOCK", "TRIPWIRE_HOOK",
            "DETECTOR_RAIL", "LIGHTNING_ROD");
    private static final Set<String> ANALOG_SOURCES = Set.of("DAYLIGHT_DETECTOR", "TARGET", "SCULK_SENSOR",
            "CALIBRATED_SCULK_SENSOR", "LIGHT_WEIGHTED_PRESSURE_PLATE", "HEAVY_WEIGHTED_PRESSURE_PLATE", "TRAPPED_CHEST");
    private static final Set<String> PISTON_LIKE = Set.of("PISTON", "STICKY_PISTON", "DISPENSER", "DROPPER");
    private static final Set<String> RECEIVERS = Set.of("REDSTONE_LAMP", "NOTE_BLOCK", "TNT", "POWERED_RAIL",
            "ACTIVATOR_RAIL", "BELL", "HOPPER", "CRAFTER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK", "IRON_DOOR", "IRON_TRAPDOOR");
    private static final Set<String> READABLE = Set.of("CHEST", "TRAPPED_CHEST", "BARREL", "FURNACE",
            "BLAST_FURNACE", "SMOKER", "BREWING_STAND", "HOPPER", "DISPENSER", "DROPPER", "CAULDRON", "WATER_CAULDRON",
            "LAVA_CAULDRON", "POWDER_SNOW_CAULDRON", "COMPOSTER", "LECTERN", "JUKEBOX", "CAKE", "END_PORTAL_FRAME",
            "BEEHIVE", "BEE_NEST", "CHISELED_BOOKSHELF", "RESPAWN_ANCHOR", "DECORATED_POT", "CRAFTER", "COMMAND_BLOCK",
            "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR");

    private final BlockReader reader;
    private final Supplier<? extends Collection<AuditPosition>> itemFrames;
    private final int worldMinY;
    private final int worldMaxY;

    public RedstoneAuditSnapshotFactory(BlockReader reader, Collection<AuditPosition> itemFrames, int worldMinY,
                                        int worldMaxY) {
        this(reader, () -> itemFrames, worldMinY, worldMaxY);
    }

    /** {@code itemFrames} is only asked for once the selection passed the size check. */
    public RedstoneAuditSnapshotFactory(BlockReader reader, Supplier<? extends Collection<AuditPosition>> itemFrames,
                                        int worldMinY, int worldMaxY) {
        this.reader = reader;
        this.itemFrames = itemFrames;
        this.worldMinY = worldMinY;
        this.worldMaxY = worldMaxY;
    }

    public Capture capture(AuditPosition min, AuditPosition max) {
        int halo = RedstoneAuditSnapshot.HALO;
        int lowY = Math.max(worldMinY, min.y() - halo);
        int highY = Math.min(worldMaxY, max.y() + halo);
        long volume = saturatedProduct(span(min.x(), max.x()), span(min.y(), max.y()), span(min.z(), max.z()));
        long readVolume = saturatedProduct(span(min.x() - halo, max.x() + halo), Math.max(0L, span(lowY, highY)),
                span(min.z() - halo, max.z() + halo));
        WorkEstimate estimate = OperationLimits.checkRedstoneAudit(readVolume);
        if (estimate.hardRejected()) {
            return Capture.refused("This selection is too large: " + volume + " blocks (about " + readVolume
                    + " with the " + halo + "-block margin the audit also reads); the audit reads at most "
                    + OperationLimits.REDSTONE_AUDIT_HARD_MAX + ". Select just the machine.");
        }
        Set<AuditPosition> frames = new HashSet<>(itemFrames.get());
        RedstoneAuditSnapshot.Builder builder = RedstoneAuditSnapshot.builder(min, max);
        for (int x = min.x() - halo; x <= max.x() + halo; x++) {
            for (int y = lowY; y <= highY; y++) {
                for (int z = min.z() - halo; z <= max.z() + halo; z++) {
                    AuditPosition position = new AuditPosition(x, y, z);
                    AuditCell cell = classify(reader.read(x, y, z));
                    if (frames.contains(position) && !cell.readable()) {
                        cell = cell.isEmpty() ? AuditCell.readableBlock(false) : cell.asReadable();
                    }
                    builder.put(position, cell);
                }
            }
        }
        return Capture.of(builder.build());
    }

    private static long span(int low, int high) {
        return (long) high - low + 1;
    }

    private static long saturatedProduct(long a, long b, long c) {
        long ab = a == 0 || b == 0 ? 0 : (a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b);
        return ab == 0 || c == 0 ? 0 : (ab > Long.MAX_VALUE / c ? Long.MAX_VALUE : ab * c);
    }

    /** What a block is to redstone. Unknown blocks are conductors if they occlude, otherwise inert. */
    public static AuditCell classify(BlockData data) {
        if (data == null) {
            return AuditCell.EMPTY;
        }
        if (data instanceof RedstoneWire wire) {
            Map<Side, WireLink> links = new EnumMap<>(Side.class);
            for (Side side : Side.HORIZONTAL) {
                links.put(side, link(wire.getFace(face(side))));
            }
            return AuditCell.dust(links);
        }
        if (data instanceof Repeater repeater) {
            return AuditCell.repeater(side(repeater.getFacing()));
        }
        if (data instanceof Comparator comparator) {
            return AuditCell.comparator(side(comparator.getFacing()));
        }
        if (data instanceof Observer observer) {
            return AuditCell.observer(side(observer.getFacing()));
        }
        if (data instanceof RedstoneWallTorch torch) {
            return AuditCell.wallTorch(side(torch.getFacing()));
        }
        String name = data.getMaterial().name();
        if (name.equals("REDSTONE_TORCH")) {
            return AuditCell.torch();
        }
        if (name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR")) {
            return AuditCell.EMPTY;
        }
        boolean occluding = data.isOccluding();
        boolean fullSource = FULL_SOURCES.contains(name) || name.endsWith("_BUTTON")
                || (name.endsWith("_PRESSURE_PLATE") && !ANALOG_SOURCES.contains(name));
        boolean analogSource = ANALOG_SOURCES.contains(name);
        boolean pistonLike = PISTON_LIKE.contains(name);
        boolean receiver = pistonLike || RECEIVERS.contains(name) || name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE") || name.endsWith("COPPER_BULB");
        boolean readable = READABLE.contains(name) || name.endsWith("SHULKER_BOX") || name.endsWith("CANDLE_CAKE");
        if (!fullSource && !analogSource && !receiver && !readable) {
            return occluding ? AuditCell.conductorBlock() : AuditCell.nonConductorBlock();
        }
        boolean conductor = occluding && !fullSource && !pistonLike || name.equals("TARGET");
        return AuditCell.blockWith(conductor, fullSource, analogSource, receiver, pistonLike, readable);
    }

    private static WireLink link(RedstoneWire.Connection connection) {
        if (connection == null) {
            return WireLink.NONE;
        }
        return switch (connection) {
            case SIDE -> WireLink.SIDE;
            case UP -> WireLink.UP;
            default -> WireLink.NONE;
        };
    }

    static Side side(BlockFace face) {
        return switch (face) {
            case NORTH -> Side.NORTH;
            case SOUTH -> Side.SOUTH;
            case EAST -> Side.EAST;
            case WEST -> Side.WEST;
            case UP -> Side.UP;
            case DOWN -> Side.DOWN;
            default -> throw new IllegalArgumentException("not a block face: " + face.name().toLowerCase(Locale.ROOT));
        };
    }

    private static BlockFace face(Side side) {
        return BlockFace.valueOf(side.name());
    }
}
