package com.bayzyl.redstone;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * What the audit knows about one block, detached from the server.
 *
 * <p>{@code facing} follows Minecraft's block state: for repeaters and comparators it points at the input (the
 * output is the opposite side); for observers it points at the observed block (the output is the opposite side);
 * for wall torches it points away from the wall. {@code wire} holds the server-computed dust connections for the
 * four horizontal sides.
 */
public record AuditCell(AuditKind kind, Side facing, boolean conductor, boolean source, boolean analogSource,
                        boolean receiver, boolean pistonLike, boolean readable, Map<Side, WireLink> wire) {

    public enum AuditKind { EMPTY, BLOCK, DUST, REPEATER, COMPARATOR, OBSERVER, TORCH, WALL_TORCH }

    /** A dust side: no connection, a connection on the same level (or stepping down), or one stepping up. */
    public enum WireLink { NONE, SIDE, UP }

    public static final AuditCell EMPTY = new AuditCell(AuditKind.EMPTY, null, false, false, false, false, false, false, Map.of());

    public AuditCell {
        Objects.requireNonNull(kind, "kind");
        wire = wire.isEmpty() ? Map.of() : Map.copyOf(wire);
    }

    // ---- factories -------------------------------------------------------------------------------------------

    /** A full opaque block that carries power (stone, planks, ...). */
    public static AuditCell conductorBlock() {
        return block(true);
    }

    /** A block that does not carry power (glass, slabs, leaves, slime, ...). */
    public static AuditCell nonConductorBlock() {
        return block(false);
    }

    private static AuditCell block(boolean conductor) {
        return new AuditCell(AuditKind.BLOCK, null, conductor, false, false, false, false, false, Map.of());
    }

    /** Dust with the given connected sides; unspecified horizontal sides are {@link WireLink#NONE}. */
    public static AuditCell dust(Map<Side, WireLink> wire) {
        Map<Side, WireLink> sides = new EnumMap<>(Side.class);
        for (Side side : Side.HORIZONTAL) {
            sides.put(side, wire.getOrDefault(side, WireLink.NONE));
        }
        return new AuditCell(AuditKind.DUST, null, false, false, false, false, false, false, sides);
    }

    public static AuditCell repeater(Side facing) {
        return directional(AuditKind.REPEATER, facing);
    }

    public static AuditCell comparator(Side facing) {
        return directional(AuditKind.COMPARATOR, facing);
    }

    public static AuditCell observer(Side facing) {
        return directional(AuditKind.OBSERVER, facing);
    }

    /** A redstone torch standing on the block below it. */
    public static AuditCell torch() {
        return new AuditCell(AuditKind.TORCH, null, false, false, false, false, false, false, Map.of());
    }

    /** A redstone torch on a wall; {@code facing} points away from the wall. */
    public static AuditCell wallTorch(Side facing) {
        return directional(AuditKind.WALL_TORCH, facing);
    }

    private static AuditCell directional(AuditKind kind, Side facing) {
        return new AuditCell(kind, Objects.requireNonNull(facing, "facing"), false, false, false, false, false, false, Map.of());
    }

    /** A full-strength power source (lever, button, pressure plate, redstone block, tripwire hook, ...). */
    public static AuditCell sourceBlock() {
        return new AuditCell(AuditKind.BLOCK, null, false, true, false, false, false, false, Map.of());
    }

    /** A source whose strength varies (daylight sensor, target, sculk sensor, weighted plate, trapped chest). */
    public static AuditCell analogSourceBlock() {
        return new AuditCell(AuditKind.BLOCK, null, false, false, true, false, false, false, Map.of());
    }

    /** Something that acts when powered (lamp, door, note block, TNT, rail, bell, ...). */
    public static AuditCell receiverBlock(boolean conductor) {
        return new AuditCell(AuditKind.BLOCK, null, conductor, false, false, true, false, false, Map.of());
    }

    /** A piston, dispenser, or dropper: powered also through quasi-connectivity. */
    public static AuditCell pistonBlock() {
        return new AuditCell(AuditKind.BLOCK, null, false, false, false, true, true, false, Map.of());
    }

    /** A block a comparator can measure (containers, cauldrons, composters, item frames, ...). */
    public static AuditCell readableBlock(boolean conductor) {
        return new AuditCell(AuditKind.BLOCK, null, conductor, false, false, false, false, true, Map.of());
    }

    public AuditCell asReadable() {
        return new AuditCell(kind, facing, conductor, source, analogSource, receiver, pistonLike, true, wire);
    }

    public AuditCell asAnalogSource() {
        return new AuditCell(kind, facing, conductor, source, true, receiver, pistonLike, readable, wire);
    }

    // ---- topology helpers ------------------------------------------------------------------------------------

    public boolean isEmpty() {
        return kind == AuditKind.EMPTY;
    }

    public boolean isDust() {
        return kind == AuditKind.DUST;
    }

    public boolean isDiode() {
        return kind == AuditKind.REPEATER || kind == AuditKind.COMPARATOR;
    }

    public boolean isTorch() {
        return kind == AuditKind.TORCH || kind == AuditKind.WALL_TORCH;
    }

    /** The side a repeater, comparator, or observer outputs to; null for everything else. */
    public Side outputSide() {
        return isDiode() || kind == AuditKind.OBSERVER ? facing.opposite() : null;
    }

    /** The side a repeater or comparator reads its main input from; null for everything else. */
    public Side inputSide() {
        return isDiode() ? facing : null;
    }

    /** The side of the block a torch hangs on or stands on; null for everything else. */
    public Side attachedSide() {
        if (kind == AuditKind.TORCH) {
            return Side.DOWN;
        }
        return kind == AuditKind.WALL_TORCH ? facing.opposite() : null;
    }

    public WireLink wire(Side side) {
        return wire.getOrDefault(side, WireLink.NONE);
    }

    /** True for anything that originates a signal by itself (sources, torches, diodes, observers). */
    public boolean emits() {
        return source || analogSource || isTorch() || isDiode() || kind == AuditKind.OBSERVER;
    }

    /** True for anything that originates a full-strength (15) signal. */
    public boolean emitsFullStrength() {
        return source || isTorch() || kind == AuditKind.REPEATER || kind == AuditKind.OBSERVER;
    }
}
