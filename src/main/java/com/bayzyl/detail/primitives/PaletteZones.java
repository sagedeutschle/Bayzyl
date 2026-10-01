package com.bayzyl.detail.primitives;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PaletteZones {
    private final List<WeightedMaterial> core;
    private final List<WeightedMaterial> mid;
    private final List<WeightedMaterial> edge;

    private PaletteZones(List<WeightedMaterial> core, List<WeightedMaterial> mid, List<WeightedMaterial> edge) {
        this.core = core;
        this.mid = mid;
        this.edge = edge;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Material pick(double zoneT, Random random) {
        List<WeightedMaterial> bucket;
        if (zoneT < 0.34) {
            bucket = core.isEmpty() ? mid : core;
        } else if (zoneT < 0.67) {
            bucket = mid.isEmpty() ? (core.isEmpty() ? edge : core) : mid;
        } else {
            bucket = edge.isEmpty() ? mid : edge;
        }
        return weightedPick(bucket, random);
    }

    private Material weightedPick(List<WeightedMaterial> bucket, Random random) {
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        double total = 0;
        for (WeightedMaterial wm : bucket) {
            total += wm.weight;
        }
        if (total <= 0) {
            return bucket.get(random.nextInt(bucket.size())).material;
        }
        double roll = random.nextDouble() * total;
        double accum = 0;
        for (WeightedMaterial wm : bucket) {
            accum += wm.weight;
            if (roll <= accum) {
                return wm.material;
            }
        }
        return bucket.get(bucket.size() - 1).material;
    }

    public static final class Builder {
        private final List<WeightedMaterial> core = new ArrayList<>();
        private final List<WeightedMaterial> mid = new ArrayList<>();
        private final List<WeightedMaterial> edge = new ArrayList<>();

        public Builder core(Material material, double weight) {
            core.add(new WeightedMaterial(material, weight));
            return this;
        }

        public Builder mid(Material material, double weight) {
            mid.add(new WeightedMaterial(material, weight));
            return this;
        }

        public Builder edge(Material material, double weight) {
            edge.add(new WeightedMaterial(material, weight));
            return this;
        }

        public PaletteZones build() {
            return new PaletteZones(core, mid, edge);
        }
    }

    private static final class WeightedMaterial {
        final Material material;
        final double weight;

        WeightedMaterial(Material material, double weight) {
            this.material = material;
            this.weight = weight;
        }
    }
}
