package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.AuditKind;
import com.bayzyl.redstone.AuditCell.WireLink;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared static redstone topology. Methods that may need a cell outside the captured area return a {@link Boolean}
 * where null means "unknown": detectors stay silent rather than guess.
 */
final class Topology {
    private Topology() {
    }

    enum Feed { NONE, ANALOG, FULL }

    enum LinkKind { CONTINUES, DELIVERS, FEEDS, LOOSE, UNKNOWN }

    /** How one connected dust side behaves; {@code next} is the neighbouring dust for CONTINUES. */
    record Link(Side side, LinkKind kind, AuditPosition next) {
    }

    /** A connected run of dust, with what is known about each piece. */
    record Network(Set<AuditPosition> dust, Map<AuditPosition, List<Link>> links, boolean unknown) {
    }

    // ---- conductors --------------------------------------------------------------------------------------------

    /** Can anything around the conductor at {@code pos} (other than {@code except}) power it? */
    static Boolean conductorPowerable(RedstoneAuditSnapshot snapshot, AuditPosition pos, AuditPosition except) {
        boolean unknown = false;
        for (Side side : Side.values()) {
            AuditPosition neighbour = pos.relative(side);
            if (neighbour.equals(except)) {
                continue;
            }
            AuditCell cell = snapshot.at(neighbour);
            if (cell == null) {
                unknown = true;
            } else if (cell.source() || cell.analogSource()) {
                return true;
            } else if (cell.isDiode() || cell.kind() == AuditKind.OBSERVER) {
                if (neighbour.relative(cell.outputSide()).equals(pos)) {
                    return true;
                }
            } else if (cell.isTorch()) {
                if (side == Side.DOWN) {
                    return true;
                }
            } else if (cell.isDust()) {
                if (side == Side.UP || (side.horizontal() && cell.wire(side.opposite()) != WireLink.NONE)) {
                    return true;
                }
            }
        }
        return unknown ? null : false;
    }

    /** Is the conductor at {@code pos} strongly powered (so it also feeds adjacent dust)? */
    static Feed conductorStrongFeed(RedstoneAuditSnapshot snapshot, AuditPosition pos) {
        Feed best = Feed.NONE;
        for (Side side : Side.values()) {
            AuditPosition neighbour = pos.relative(side);
            AuditCell cell = snapshot.at(neighbour);
            if (cell == null) {
                continue;
            }
            if (cell.source()) {
                return Feed.FULL;
            }
            if ((cell.kind() == AuditKind.REPEATER || cell.kind() == AuditKind.OBSERVER)
                    && neighbour.relative(cell.outputSide()).equals(pos)) {
                return Feed.FULL;
            }
            if (cell.isTorch() && side == Side.DOWN) {
                return Feed.FULL;
            }
            if (cell.kind() == AuditKind.COMPARATOR && neighbour.relative(cell.outputSide()).equals(pos)) {
                best = Feed.ANALOG;
            }
        }
        return best;
    }

    /** Does something use the power of a conductor at {@code pos}? Strongly powered blocks also feed dust. */
    static Boolean conductorHasConsumers(RedstoneAuditSnapshot snapshot, AuditPosition pos, boolean strong,
                                         AuditPosition except) {
        boolean unknown = false;
        for (Side side : Side.values()) {
            AuditPosition neighbour = pos.relative(side);
            if (neighbour.equals(except)) {
                continue;
            }
            AuditCell cell = snapshot.at(neighbour);
            if (cell == null) {
                unknown = true;
            } else if (cell.receiver()) {
                return true;
            } else if (cell.isDiode() && neighbour.relative(cell.inputSide()).equals(pos)) {
                return true;
            } else if (cell.isTorch() && neighbour.relative(cell.attachedSide()).equals(pos)) {
                return true;
            } else if (strong && cell.isDust()) {
                return true;
            }
        }
        return unknown ? null : false;
    }

    // ---- dust --------------------------------------------------------------------------------------------------

    /** The strongest feed reaching the dust at {@code pos} from non-dust neighbours; null if unknown and unfed. */
    static Feed dustFeed(RedstoneAuditSnapshot snapshot, AuditPosition pos) {
        Feed best = Feed.NONE;
        boolean unknown = false;
        for (Side side : Side.values()) {
            AuditPosition neighbour = pos.relative(side);
            AuditCell cell = snapshot.at(neighbour);
            if (cell == null) {
                unknown = true;
                continue;
            }
            Feed feed = Feed.NONE;
            if (cell.source() || cell.isTorch()) {
                feed = Feed.FULL;
            } else if (cell.analogSource()) {
                feed = Feed.ANALOG;
            } else if ((cell.isDiode() || cell.kind() == AuditKind.OBSERVER)
                    && neighbour.relative(cell.outputSide()).equals(pos)) {
                feed = cell.kind() == AuditKind.COMPARATOR ? Feed.ANALOG : Feed.FULL;
            } else if (cell.conductor()) {
                feed = conductorStrongFeed(snapshot, neighbour);
            }
            if (feed.ordinal() > best.ordinal()) {
                best = feed;
            }
        }
        return best == Feed.NONE && unknown ? null : best;
    }

    /** Does the dust at {@code pos} power anything other than more dust (below it or where it points)? */
    static Boolean dustDelivers(RedstoneAuditSnapshot snapshot, AuditPosition pos, AuditCell dust) {
        boolean unknown = false;
        AuditPosition below = pos.relative(Side.DOWN);
        AuditCell under = snapshot.at(below);
        if (under == null) {
            unknown = true;
        } else if (under.receiver()) {
            return true;
        } else if (under.conductor()) {
            Boolean consumers = conductorHasConsumers(snapshot, below, false, pos);
            if (consumers == null) {
                unknown = true;
            } else if (consumers) {
                return true;
            }
        }
        for (Link link : links(snapshot, pos, dust)) {
            if (link.kind() == LinkKind.DELIVERS) {
                return true;
            }
            if (link.kind() == LinkKind.UNKNOWN) {
                unknown = true;
            }
        }
        return unknown ? null : false;
    }

    /** Classifies every connected side of the dust at {@code pos}. */
    static List<Link> links(RedstoneAuditSnapshot snapshot, AuditPosition pos, AuditCell dust) {
        List<Link> links = new ArrayList<>();
        for (Side side : Side.HORIZONTAL) {
            WireLink wire = dust.wire(side);
            if (wire != WireLink.NONE) {
                links.add(link(snapshot, pos, side, wire));
            }
        }
        return links;
    }

    private static Link link(RedstoneAuditSnapshot snapshot, AuditPosition pos, Side side, WireLink wire) {
        AuditPosition neighbour = pos.relative(side);
        AuditCell cell = snapshot.at(neighbour);
        if (cell == null) {
            return new Link(side, LinkKind.UNKNOWN, null);
        }
        if (wire == WireLink.UP) {
            AuditPosition above = neighbour.relative(Side.UP);
            AuditCell upper = snapshot.at(above);
            if (upper == null) {
                return new Link(side, LinkKind.UNKNOWN, null);
            }
            return upper.isDust() ? new Link(side, LinkKind.CONTINUES, above) : new Link(side, LinkKind.LOOSE, null);
        }
        if (cell.isDust()) {
            return new Link(side, LinkKind.CONTINUES, neighbour);
        }
        if (cell.receiver()) {
            return new Link(side, LinkKind.DELIVERS, null);
        }
        if (cell.isDiode()) {
            if (neighbour.relative(cell.inputSide()).equals(pos)) {
                return new Link(side, LinkKind.DELIVERS, null);
            }
            if (cell.kind() == AuditKind.COMPARATOR && side.perpendicularTo(cell.facing())) {
                return new Link(side, LinkKind.DELIVERS, null);
            }
            return neighbour.relative(cell.outputSide()).equals(pos)
                    ? new Link(side, LinkKind.FEEDS, null) : new Link(side, LinkKind.LOOSE, null);
        }
        if (cell.emits()) {
            return new Link(side, LinkKind.FEEDS, null);
        }
        if (cell.conductor()) {
            Boolean consumers = conductorHasConsumers(snapshot, neighbour, false, pos);
            if (consumers == null) {
                return new Link(side, LinkKind.UNKNOWN, null);
            }
            if (consumers) {
                return new Link(side, LinkKind.DELIVERS, null);
            }
            return conductorStrongFeed(snapshot, neighbour) != Feed.NONE
                    ? new Link(side, LinkKind.FEEDS, null) : new Link(side, LinkKind.LOOSE, null);
        }
        AuditPosition stepDown = neighbour.relative(Side.DOWN);
        AuditCell lower = snapshot.at(stepDown);
        if (lower == null) {
            return new Link(side, LinkKind.UNKNOWN, null);
        }
        return lower.isDust() ? new Link(side, LinkKind.CONTINUES, stepDown) : new Link(side, LinkKind.LOOSE, null);
    }

    /** Groups all captured dust into connected networks. */
    static List<Network> networks(RedstoneAuditSnapshot snapshot) {
        Map<AuditPosition, List<Link>> allLinks = new HashMap<>();
        Map<AuditPosition, Set<AuditPosition>> adjacency = new HashMap<>();
        for (Map.Entry<AuditPosition, AuditCell> entry : snapshot.cells().entrySet()) {
            if (!entry.getValue().isDust()) {
                continue;
            }
            List<Link> links = links(snapshot, entry.getKey(), entry.getValue());
            allLinks.put(entry.getKey(), links);
            adjacency.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>());
            for (Link link : links) {
                if (link.kind() == LinkKind.CONTINUES) {
                    adjacency.get(entry.getKey()).add(link.next());
                    adjacency.computeIfAbsent(link.next(), ignored -> new LinkedHashSet<>()).add(entry.getKey());
                }
            }
        }
        List<Network> networks = new ArrayList<>();
        Set<AuditPosition> visited = new java.util.HashSet<>();
        List<AuditPosition> starts = new ArrayList<>(allLinks.keySet());
        starts.sort(null);
        for (AuditPosition start : starts) {
            if (!visited.add(start)) {
                continue;
            }
            Set<AuditPosition> members = new LinkedHashSet<>();
            Map<AuditPosition, List<Link>> memberLinks = new LinkedHashMap<>();
            boolean unknown = false;
            ArrayDeque<AuditPosition> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                AuditPosition current = queue.removeFirst();
                members.add(current);
                List<Link> links = allLinks.getOrDefault(current, List.of());
                memberLinks.put(current, links);
                for (Link link : links) {
                    if (link.kind() == LinkKind.UNKNOWN) {
                        unknown = true;
                    }
                }
                if (!snapshot.inSelection(current) && touchesUncaptured(snapshot, current)) {
                    unknown = true;
                }
                for (AuditPosition next : adjacency.getOrDefault(current, Set.of())) {
                    if (visited.add(next)) {
                        queue.addLast(next);
                    }
                }
            }
            networks.add(new Network(members, memberLinks, unknown));
        }
        return networks;
    }

    private static boolean touchesUncaptured(RedstoneAuditSnapshot snapshot, AuditPosition pos) {
        for (Side side : Side.values()) {
            if (!snapshot.captured(pos.relative(side))) {
                return true;
            }
        }
        return false;
    }

    // ---- wording -----------------------------------------------------------------------------------------------

    static String describe(AuditCell cell) {
        if (cell == null || cell.isEmpty()) {
            return "air";
        }
        return switch (cell.kind()) {
            case DUST -> "redstone dust";
            case REPEATER -> "a repeater";
            case COMPARATOR -> "a comparator";
            case OBSERVER -> "an observer";
            case TORCH, WALL_TORCH -> "a redstone torch";
            default -> {
                if (cell.pistonLike()) {
                    yield "a piston/dispenser/dropper";
                }
                if (cell.receiver()) {
                    yield "a powered component";
                }
                if (cell.source() || cell.analogSource()) {
                    yield "a power source";
                }
                if (cell.readable()) {
                    yield "a container";
                }
                yield cell.conductor() ? "a solid block" : "a block that does not carry power";
            }
        };
    }

    static String direction(Side side) {
        return side.name().toLowerCase(java.util.Locale.ROOT);
    }
}
