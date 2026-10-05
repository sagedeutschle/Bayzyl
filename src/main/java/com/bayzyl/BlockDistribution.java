package com.bayzyl;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class BlockDistribution {
    private final Map<Material, Double> distribution;
    private final double totalWeight;
    private final boolean singleMaterial;
    private final Material soleMaterial;

    private BlockDistribution(Map<Material, Double> distribution) {
        this.distribution = Collections.unmodifiableMap(new LinkedHashMap<>(distribution));
        this.totalWeight = this.distribution.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!Double.isFinite(totalWeight) || totalWeight <= 0.0D) {
            throw new IllegalArgumentException("Distribution weights must have a finite positive total.");
        }
        this.singleMaterial = this.distribution.size() == 1;
        this.soleMaterial = this.singleMaterial ? this.distribution.keySet().iterator().next() : null;
    }

    /**
     * Parses a string to create a weighted distribution of blocks.
     *
     * Supported forms:
     * - `stone`
     * - `stone,dirt,granite`
     * - `50%stone,25%dirt,25%granite`
     * - `stone:50%,dirt:25%,granite:25%`
     *
     * Percentages are relative weights. They do not need to add up to 100.
     *
     * @param input the input string
     * @return a block distribution
     */
    public static BlockDistribution parse(String input) {
        Objects.requireNonNull(input, "Input cannot be null");
        String normalizedInput = input.trim();
        if (normalizedInput.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be empty");
        }

        String[] rawParts = normalizedInput.split(",");
        if (rawParts.length == 1) {
            ParsedPart part = parsePart(rawParts[0]);
            if (part.weight() == null) {
                return new BlockDistribution(Map.of(part.material(), 100.0));
            }
            throw new IllegalArgumentException("Weighted distributions need at least two materials.");
        }

        Map<Material, Double> weights = new LinkedHashMap<>();
        boolean hasExplicitWeight = false;
        for (String rawPart : rawParts) {
            ParsedPart part = parsePart(rawPart);
            double weight = part.weight() == null ? 1.0D : part.weight();
            if (!Double.isFinite(weight) || weight <= 0.0D) {
                throw new IllegalArgumentException("Weights must be greater than 0.");
            }
            hasExplicitWeight |= part.weight() != null;
            weights.merge(part.material(), weight, Double::sum);
        }

        if (weights.isEmpty()) {
            throw new IllegalArgumentException("No valid materials in distribution.");
        }

        if (hasExplicitWeight && weights.size() == 1) {
            throw new IllegalArgumentException("Weighted distributions need at least two materials.");
        }

        return new BlockDistribution(weights);
    }

    private static Material parseMaterial(String materialName) {
        Material material = EditUtil.parseBlock(materialName);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + materialName);
        }
        return material;
    }

    /**
     * Returns true if this distribution represents a single material with 100% probability.
     */
    public boolean isSingleMaterial() {
        return singleMaterial;
    }

    /**
     * Returns the sole material if this is a single-material distribution.
     * Throws an IllegalStateException if not a single-material distribution.
     */
    public Material getSoleMaterial() {
        if (!singleMaterial) {
            throw new IllegalStateException("Not a single material distribution.");
        }
        return soleMaterial;
    }

    public Map<Material, Double> weights() {
        return distribution;
    }

    public List<Entry> entries() {
        return distribution.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    public double totalWeight() {
        return totalWeight;
    }

    /**
     * Picks a random material from the distribution based on its percentages.
     * @return A randomly selected Material.
     */
    public Material pickRandom() {
        return pickRandom(ThreadLocalRandom.current());
    }

    public Material pickRandom(Random random) {
        if (singleMaterial) {
            return soleMaterial;
        }

        double rand = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (Map.Entry<Material, Double> entry : distribution.entrySet()) {
            cumulative += entry.getValue();
            if (rand <= cumulative) {
                return entry.getKey();
            }
        }
        // Fallback in case of floating point inaccuracies not caught by sum check
        return distribution.keySet().iterator().next();
    }

    @Override
    public String toString() {
        if (singleMaterial) {
            return soleMaterial.name().toLowerCase();
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Double> entry : distribution.entrySet()) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            double percent = (entry.getValue() * 100.0D) / totalWeight;
            sb.append(String.format(java.util.Locale.ROOT, "%.1f", percent))
                    .append("%")
                    .append(entry.getKey().name().toLowerCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockDistribution that = (BlockDistribution) o;
        return singleMaterial == that.singleMaterial &&
                Objects.equals(soleMaterial, that.soleMaterial) &&
                distribution.equals(that.distribution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(distribution, singleMaterial, soleMaterial);
    }

    private static ParsedPart parsePart(String rawPart) {
        String token = rawPart == null ? "" : rawPart.trim();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Material entry cannot be empty.");
        }

        String materialToken = token;
        Double weight = null;

        int percentIndex = token.indexOf('%');
        if (percentIndex > 0 && percentIndex < token.length() - 1) {
            String weightToken = token.substring(0, percentIndex).trim();
            String remainder = token.substring(percentIndex + 1).trim();
            if (isNumeric(weightToken)) {
                weight = Double.parseDouble(weightToken);
                materialToken = remainder;
            }
        }

        if (weight == null) {
            int colonIndex = token.lastIndexOf(':');
            if (colonIndex > 0 && token.endsWith("%")) {
                String weightToken = token.substring(colonIndex + 1, token.length() - 1).trim();
                if (isNumeric(weightToken)) {
                    weight = Double.parseDouble(weightToken);
                    materialToken = token.substring(0, colonIndex).trim();
                }
            }
        }

        Material material = parseMaterial(materialToken);
        return new ParsedPart(material, weight);
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public record Entry(Material material, double weight) {
    }

    private record ParsedPart(Material material, Double weight) {
    }
}
