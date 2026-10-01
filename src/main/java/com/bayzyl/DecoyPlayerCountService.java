package com.bayzyl;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DecoyPlayerCountService {
    private static final int MAX_DECOYS = 500;
    private static final List<String> NAME_PREFIXES = List.of(
            "Stone", "River", "Cedar", "Copper", "Moss", "Granite", "Echo", "Cloud",
            "Harbor", "Iron", "Hollow", "Summit", "Ash", "Cinder", "Maple", "Slate"
    );
    private static final List<String> NAME_SUFFIXES = List.of(
            "Fox", "Vale", "Wing", "Forge", "Bloom", "Drift", "Brook", "Field",
            "Torch", "Grove", "Brick", "Trail", "Quill", "Cove", "Peak", "Branch"
    );

    private volatile int decoyCount;
    private volatile List<DecoyProfile> decoys = List.of();

    public int decoyCount() {
        return decoyCount;
    }

    public void setDecoyCount(int decoyCount) {
        int clamped = Math.max(0, Math.min(MAX_DECOYS, decoyCount));
        this.decoyCount = clamped;
        this.decoys = generateDecoys(clamped);
    }

    public int effectiveOnlinePlayers() {
        return Bukkit.getOnlinePlayers().size() + decoyCount;
    }

    public List<DecoyProfile> decoys() {
        return decoys;
    }

    public int maxDecoys() {
        return MAX_DECOYS;
    }

    private List<DecoyProfile> generateDecoys(int count) {
        if (count <= 0) {
            return List.of();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<DecoyProfile> generated = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            generated.add(new DecoyProfile(UUID.randomUUID(), randomName(random, i)));
        }
        return List.copyOf(generated);
    }

    private String randomName(ThreadLocalRandom random, int index) {
        String base = NAME_PREFIXES.get(random.nextInt(NAME_PREFIXES.size()))
                + NAME_SUFFIXES.get(random.nextInt(NAME_SUFFIXES.size()));
        String suffix = Integer.toString((index + 1) % 10000);
        String candidate = base + suffix;
        if (candidate.length() <= 16) {
            return candidate;
        }
        int trimTo = Math.max(1, 16 - suffix.length());
        return candidate.substring(0, trimTo).toLowerCase(Locale.ROOT) + suffix;
    }

    public record DecoyProfile(UUID id, String name) {
    }
}
