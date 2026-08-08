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
 * band inside solid rock, vertically scaled with terrain height. Nothing at or below sea level
 * is ever carved (water can never flood the terrain interior), the top of every column stays
 * solid, and the band is mostly solid with occasional cheese voids — so it cannot hollow the
 * surface into flat sea or floating islands.
 */
public abstract class AbstractTerrainDiffusionDensityFunction implements DensityFunction {
    /** Lowest density this function can report (blocks below the target height). */
    public static final double MIN_DENSITY = -1;
    /** Highest density this function can report (blocks above the target height). */
    public static final double MAX_DENSITY = 1;

    /** Whether to apply the height-scaled cheese-cave band on top of the terrain step. */
    protected final boolean caves;

    // Cave band tuning. The original 4*noise^2-1.5 made the band ~95% hollow (void everywhere),
    // so lowland interiors sat below sea level and flooded into "flat sea + void". Reworked:
    // 1 - CAVE_STRENGTH*|noise| keeps the band mostly solid with occasional cheese voids, and
    // nothing below sea level is ever carved (see density()).
    private static final float CAVE_FREQ = 0.01f;
    private static final float CAVE_Y_SCALE = 8.0f;
    /** Cheese strength: 1 - K*|noise| goes void where |noise| > 1/K (~13% of the band). */
    private static final float CAVE_STRENGTH = 7.0f;
    private static final int CAVE_MIN_DEPTH = 48;
    private static final int CAVE_MAX_DEPTH = 256;
    private static final int CAVE_FADE = 16;
    /** Blocks of solid terrain kept between the surface and the cave band. */
    private static final int CAVE_SURFACE_CAP = 8;
    /** Sea level: nothing at or below this is ever carved, so water can never flood the interior. */
    private static final int SEA_LEVEL = 63;

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
     * Density at a single block. Without caves this is a plain step at {@code targetHeight}.
     * With caves, a cheese-cave band sits in solid rock: at or below sea level nothing is ever
     * carved (water can never flood the terrain interior), the top {@value #CAVE_SURFACE_CAP}
     * blocks of every column stay solid, and the band (faded in from both the sea-level floor
     * and the surface cap) is mostly solid with occasional voids where |noise| is large.
     */
    private double density(int x, int y, int z, int targetHeight) {
        if (y >= targetHeight) {
            return -1.0;
        }
        if (!caves) {
            return 1.0;
        }
        if (y <= SEA_LEVEL) {
            return 1.0;
        }
        int caveTop = targetHeight - CAVE_SURFACE_CAP;
        if (y >= caveTop) {
            return 1.0;
        }
        int floorY = Math.max(SEA_LEVEL, targetHeight - caveDepth(targetHeight));
        if (y <= floorY) {
            return 1.0;
        }
        float bottomFade = Math.min(1.0f, (y - floorY) / (float) CAVE_FADE);
        float topFade = Math.min(1.0f, (caveTop - y) / (float) CAVE_FADE);
        float w = bottomFade * topFade;
        float n = caveNoise().GetNoise(x * CAVE_FREQ, y * CAVE_FREQ * CAVE_Y_SCALE, z * CAVE_FREQ);
        double cave = 1.0 - CAVE_STRENGTH * Math.abs(n);
        return clampDensity(w * cave + (1.0 - w));
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
