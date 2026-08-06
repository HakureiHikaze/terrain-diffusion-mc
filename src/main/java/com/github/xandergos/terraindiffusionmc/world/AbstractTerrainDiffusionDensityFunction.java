package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.FastNoiseLite;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Shared logic for the terrain-diffusion density function.
 *
 * <p>Everything that is identical across the supported Minecraft drops lives here. The value-range
 * declaration is the one part of {@link DensityFunction} that changed - 26.1
 * and 26.2 expose {@code minValue()}/{@code maxValue()}, while 26.3 replaced them with a single
 * {@code range()} returning {@code net.minecraft.util.Interval}. That method, plus the codec plumbing, is supplied by the tiny version-specific
 * {@code TerrainDiffusionDensityFunction} subclass selected by the build.
 *
 * <p>When {@code caves} is enabled, the density combines the terrain step with a cheese-cave
 * noise band whose vertical range scales with the terrain height ({@code [targetHeight -
 * caveDepth, targetHeight]}). The deeper column below is always solid, so tall narrow peaks can
 * never be hollowed out into floating islands.
 */
public abstract class AbstractTerrainDiffusionDensityFunction implements DensityFunction {
    /** Lowest density this function can report (blocks below the target height). */
    public static final double MIN_DENSITY = -1;
    /** Highest density this function can report (blocks above the target height). */
    public static final double MAX_DENSITY = 1;

    /** Whether to apply the height-scaled cheese-cave band on top of the terrain step. */
    protected final boolean caves;

    // Cave band tuning: noise frequency matches the former JSON xz_scale 1.0 / y_scale 8.0.
    private static final float CAVE_FREQ = 0.01f;
    private static final float CAVE_Y_SCALE = 8.0f;
    private static final float CAVE_MIN = 1.5f;
    private static final int CAVE_MIN_DEPTH = 48;
    private static final int CAVE_MAX_DEPTH = 256;
    private static final int CAVE_FADE = 16;

    private volatile long caveNoiseSeed = Long.MIN_VALUE;
    private volatile FastNoiseLite caveNoise;

    protected AbstractTerrainDiffusionDensityFunction(boolean caves) {
        this.caves = caves;
    }

    /**
     * Cheese-cave band depth below the terrain surface, scaling with terrain height so lowlands
     * keep shallow caves and tall mountains stay firmly anchored to the ground.
     */
    private static int caveDepth(int targetHeight) {
        return Math.min(CAVE_MAX_DEPTH, Math.max(CAVE_MIN_DEPTH, targetHeight / 4));
    }

    private FastNoiseLite caveNoise() {
        long seed = LocalTerrainProvider.getSeed();
        if (seed != caveNoiseSeed) {
            synchronized (this) {
                if (seed != caveNoiseSeed) {
                    FastNoiseLite fnl = new FastNoiseLite((int) ((seed ^ 0x5DEECE66DL) & 0x7FFFFFFFL));
                    fnl.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
                    fnl.SetFractalType(FastNoiseLite.FractalType.FBm);
                    fnl.SetFractalOctaves(2);
                    fnl.SetFractalGain(0.5f);
                    fnl.SetFrequency(CAVE_FREQ);
                    caveNoise = fnl;
                    caveNoiseSeed = seed;
                }
            }
        }
        return caveNoise;
    }

    private static double clampDensity(double v) {
        return v < MIN_DENSITY ? MIN_DENSITY : (v > MAX_DENSITY ? MAX_DENSITY : v);
    }

    /**
     * Density at a single block. Without caves this is a plain step at {@code targetHeight};
     * with caves, the cheese cave band only reaches {@code caveDepth} blocks below the surface
     * and everything deeper stays solid.
     */
    private double density(int x, int y, int z, int targetHeight) {
        if (y >= targetHeight) {
            return -1.0;
        }
        if (!caves) {
            return 1.0;
        }
        int floorY = targetHeight - caveDepth(targetHeight);
        if (y < floorY) {
            return 1.0;
        }
        float fade = Math.min(1.0f, (y - floorY) / (float) CAVE_FADE);
        float n = caveNoise().GetNoise(x * CAVE_FREQ, y * CAVE_FREQ * CAVE_Y_SCALE, z * CAVE_FREQ);
        double cave = 4.0 * n * n - CAVE_MIN;
        return clampDensity(fade * cave + (1.0 - fade));
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        int x = context.blockX();
        int z = context.blockZ();
        int y = context.blockY();

        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = x >> tileShift;
        int tileZ = z >> tileShift;

        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;
        int blockEndX = blockStartX + tileSize;
        int blockEndZ = blockStartZ + tileSize;

        HeightmapData data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        if (data == null || data.heightmap == null) {
            return 1.0;
        }

        int localX = Math.max(0, Math.min(data.width  - 1, x - blockStartX));
        int localZ = Math.max(0, Math.min(data.height - 1, z - blockStartZ));

        int targetHeight = HeightConverter.convertToMinecraftHeight(data.heightmap[localZ][localX]);
        return density(x, y, z, targetHeight);
    }

    private static final class FillContext {
        int blockStartX, blockStartZ, blockEndX, blockEndZ;
        HeightmapData data;

        void update(int x, int z) {
            if (x < blockStartX || x >= blockEndX) this.init(x, z);
            if (z < blockStartZ || z >= blockEndZ) this.init(x, z);
        }

        void init(int x, int z) {
            int tileSize = TerrainDiffusionConfig.tileSize();
            int tileShift = Integer.numberOfTrailingZeros(tileSize);

            int tileX = x >> tileShift;
            int tileZ = z >> tileShift;

            this.blockStartX = tileX << tileShift;
            this.blockStartZ = tileZ << tileShift;
            this.blockEndX = blockStartX + tileSize;
            this.blockEndZ = blockStartZ + tileSize;

            this.data = LocalTerrainProvider.getInstance()
                .fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        }
    }

    @Override
    public void fillArray(double[] densities, DensityFunction.ContextProvider contextProvider) {
        if (densities.length == 0) return;

        FillContext ctx = new FillContext();
        DensityFunction.FunctionContext pos = contextProvider.forIndex(0);
        int x = pos.blockX();
        int z = pos.blockZ();
        int y = pos.blockY();
        ctx.init(x, z);

        for (int i = 0; i < densities.length; i++) {
            pos = contextProvider.forIndex(i);
            x = pos.blockX();
            z = pos.blockZ();
            y = pos.blockY();
            ctx.update(x, z);

            HeightmapData data = ctx.data;
            if (data == null || data.heightmap == null) {
                densities[i] = 1.0;
                continue;
            }

            int localX = Math.max(0, Math.min(data.width  - 1, x - ctx.blockStartX));
            int localZ = Math.max(0, Math.min(data.height - 1, z - ctx.blockStartZ));

            int targetHeight = HeightConverter
                .convertToMinecraftHeight(data.heightmap[localZ][localX]);
            densities[i] = density(x, y, z, targetHeight);
        }
    }

    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(this);
    }

    public DensityFunction mapChildren(DensityFunction.Visitor visitor) {
        return this;
    }
}
