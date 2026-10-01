package com.bayzyl.gen.generators;

import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.FbmNoise;
import com.bayzyl.gen.primitives.Worley;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * Adds vanilla-style noise to clean terrain — knocks blocks loose,
 * shaves crests, scatters pebbles. Useful for taking shape-brush output
 * (which is too clean) and making it look weathered.
 *
 * The brush works as a small displacement field over the surface: pick a
 * surface column, raise or lower it by 1-3 blocks, sprinkle the right
 * accents per env.
 */
public final class ErodeGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "erode";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int passes = Math.max(1, ctx.params.getInt("passes", 1));
        double aggression = Math.max(0.05, Math.min(1.0, ctx.params.getFloat("aggression", 0.6)));
        double frequency = ctx.params.getFloat("frequency", 0.18);
        double cracks = Math.max(0.0, Math.min(1.0, ctx.params.getFloat("cracks", 0.35)));
        boolean dropDebris = ctx.params.getBool("debris", true);

        FbmNoise noise = new FbmNoise(ctx.seed);
        Worley worley = new Worley(ctx.seed ^ 0xC0CAL);
        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();
        Random random = new Random(ctx.seed);

        World world = ctx.target.getWorld();
        int cx = ctx.target.getBlockX();
        int cz = ctx.target.getBlockZ();

        for (int pass = 0; pass < passes; pass++) {
            double passShift = pass * 17.3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz) / radius;
                    if (dist > 1.0) {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    int surfaceY = GenHelpers.findSurfaceY(world, x, z, world.getMaxHeight() - 1);
                    if (surfaceY <= world.getMinHeight()) {
                        continue;
                    }
                    if (!ctx.maskAllows(world.getBlockAt(x, surfaceY, z).getType())) {
                        continue;
                    }
                    double n = noise.fbm2D(x * frequency + passShift, z * frequency, 3);
                    double crackField = worley.crack2D(x * frequency * 0.7, z * frequency * 0.7);
                    double delta = n * aggression * 3.0;
                    int displacement = (int) Math.round(delta);

                    if (displacement < 0) {
                        // Shave off the top.
                        for (int dy = -1; dy >= displacement; dy--) {
                            int y = surfaceY + dy + 1;
                            if (y <= world.getMinHeight()) break;
                            Material existing = ctx.changes.peek(x, y, z);
                            if (existing.isAir()) {
                                continue;
                            }
                            ctx.changes.clearAir(x, y, z);
                        }
                        int newTop = Math.max(world.getMinHeight() + 1, surfaceY + displacement);
                        ctx.changes.setMaterial(x, newTop, z, palette.cap());
                    } else if (displacement > 0) {
                        // Mound up a little.
                        for (int dy = 1; dy <= displacement; dy++) {
                            int y = surfaceY + dy;
                            if (y >= world.getMaxHeight() - 1) break;
                            ctx.changes.setMaterial(x, y, z,
                                    dy == displacement ? palette.cap() : palette.sub());
                        }
                    }

                    // Crack lines: leave a single block notch when the worley pattern is sharp.
                    if (cracks > 0 && crackField < cracks * 0.15) {
                        int notchTop = Math.max(world.getMinHeight() + 1,
                                surfaceY + Math.min(0, displacement));
                        ctx.changes.clearAir(x, notchTop, z);
                    }

                    if (dropDebris && random.nextDouble() < 0.06 * aggression) {
                        int spotY = Math.max(world.getMinHeight() + 1,
                                surfaceY + Math.max(0, displacement) + 1);
                        Material existing = ctx.changes.peek(x, spotY, z);
                        if (existing.isAir() || existing == Material.SNOW) {
                            ctx.changes.setMaterial(x, spotY, z, palette.accent());
                        }
                    }
                }
            }
        }
    }
}
