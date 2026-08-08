package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeClassifier;
import com.github.xandergos.terraindiffusionmc.pipeline.CoarseBiomeClassifier;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Biome-location searches for Terrain Diffusion worlds, routed through the
 * coarse-map classifier instead of full terrain inference.
 *
 * <p>Semantics mirror the vanilla {@code BiomeSource} implementations:
 * <ul>
 *   <li>{@link #findBiomeHorizontal} iterates the full square at the search
 *       radius on the quart grid (z-major, x-minor) with reservoir sampling,
 *       matching {@code findBiomeHorizontal(..., skipSteps=1, findClosest=false)}.</li>
 *   <li>{@link #findClosestBiome3d} follows vanilla's spiral-around order
 *       (nearest-first) at the requested horizontal sample resolution.</li>
 * </ul>
 * Both map every sample position to its containing coarse pixel and classify
 * that pixel once per coarse unit: the whole search domain is fetched as a
 * single coarse slice, so the cost is one slice instead of one per sample.
 */
public final class CoarseBiomeSearch {
    private static final Logger LOG = LoggerFactory.getLogger(CoarseBiomeSearch.class);
    private static final float MIN_WEIGHT = 1e-6f;

    private CoarseBiomeSearch() {
    }

    /**
     * Coarse-biome horizontal search with vanilla order and reservoir sampling.
     *
     * @return the sampled match at quart-snapped block coordinates, or null
     */
    public static Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
            TerrainDiffusionBiomeSource source, int originX, int originY, int originZ,
            int searchRadius, Predicate<Holder<Biome>> allowed, RandomSource random) {
        int noiseCenterX = QuartPos.fromBlock(originX);
        int noiseCenterZ = QuartPos.fromBlock(originZ);
        int noiseRadius = QuartPos.fromBlock(searchRadius);
        int blockX0 = QuartPos.toBlock(noiseCenterX - noiseRadius);
        int blockX1 = QuartPos.toBlock(noiseCenterX + noiseRadius);
        int blockZ0 = QuartPos.toBlock(noiseCenterZ - noiseRadius);
        int blockZ1 = QuartPos.toBlock(noiseCenterZ + noiseRadius);

        CoarseMap coarse;
        try {
            coarse = CoarseMap.fetch(blockX0, blockZ0, blockX1, blockZ1);
        } catch (Exception e) {
            LOG.warn("Coarse biome horizontal search failed at ({}, {}): {}", originX, originZ, e.toString());
            return null;
        }

        // Mirrors BiomeSource.findBiomeHorizontal with skipSteps=1, findClosest=false:
        // one pass over the full square at the search radius, reservoir sampling.
        Pair<BlockPos, Holder<Biome>> result = null;
        int found = 0;
        for (int dz = -noiseRadius; dz <= noiseRadius; dz++) {
            int blockZ = QuartPos.toBlock(noiseCenterZ + dz);
            for (int dx = -noiseRadius; dx <= noiseRadius; dx++) {
                int noiseX = noiseCenterX + dx;
                int blockX = QuartPos.toBlock(noiseX);
                short id = coarse.classifyPixel(blockX, blockZ);
                Holder<Biome> holder = source.getBiomeHolderById(id);
                if (holder != null && allowed.test(holder)) {
                    if (result == null || random.nextInt(found + 1) == 0) {
                        result = Pair.of(new BlockPos(blockX, originY, blockZ), holder);
                    }
                    found++;
                }
            }
        }
        return result;
    }

    /**
     * Coarse-biome nearest search following vanilla spiral order.
     *
     * @return the first matching sample position, or null
     */
    public static Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
            TerrainDiffusionBiomeSource source, BlockPos origin, int searchRadius,
            int sampleResolutionHorizontal, int sampleResolutionVertical,
            Predicate<Holder<Biome>> allowed, LevelReader level) {
        Set<Holder<Biome>> candidateBiomes = source.possibleBiomes().stream()
                .filter(allowed)
                .collect(Collectors.toUnmodifiableSet());
        if (candidateBiomes.isEmpty()) {
            return null;
        }

        int sampleRadius = Math.floorDiv(searchRadius, sampleResolutionHorizontal);
        int[] sampleYs = Mth.outFromOrigin(
                origin.getY(), level.getMinY() + 1, level.getMaxY() + 1, sampleResolutionVertical).toArray();
        int span = sampleRadius * sampleResolutionHorizontal;

        CoarseMap coarse;
        try {
            coarse = CoarseMap.fetch(
                    origin.getX() - span, origin.getZ() - span,
                    origin.getX() + span, origin.getZ() + span);
        } catch (Exception e) {
            LOG.warn("Coarse biome 3d search failed at {}: {}", origin, e.toString());
            return null;
        }

        int sampleY = sampleYs.length == 0 ? origin.getY() : sampleYs[0];
        for (BlockPos.MutableBlockPos sampleColumn : BlockPos.spiralAround(
                BlockPos.ZERO, sampleRadius, Direction.EAST, Direction.SOUTH)) {
            int blockX = origin.getX() + sampleColumn.getX() * sampleResolutionHorizontal;
            int blockZ = origin.getZ() + sampleColumn.getZ() * sampleResolutionHorizontal;
            short id = coarse.classifyPixel(blockX, blockZ);
            Holder<Biome> biome = source.getBiomeHolderById(id);
            if (biome != null && candidateBiomes.contains(biome)) {
                return Pair.of(new BlockPos(blockX, sampleY, blockZ), biome);
            }
        }
        return null;
    }

    /**
     * A coarse-map region fetched as one slice, indexed by block coordinates.
     * Data layout is {@code [7, height, width]} (channel-major, C-order).
     */
    public static final class CoarseMap {
        public final int cj0;
        public final int ci0;
        public final int width;
        public final int height;
        public final int perUnit;
        private final float[] data;

        public CoarseMap(int cj0, int ci0, int width, int height, int perUnit, float[] data) {
            this.cj0 = cj0;
            this.ci0 = ci0;
            this.width = width;
            this.height = height;
            this.perUnit = perUnit;
            this.data = data;
        }

        static CoarseMap fetch(int blockX0, int blockZ0, int blockX1, int blockZ1) throws Exception {
            int perUnit = LocalTerrainProvider.blocksPerCoarseUnit();
            int cj0 = Math.floorDiv(blockX0, perUnit);
            int ci0 = Math.floorDiv(blockZ0, perUnit);
            int cj1 = Math.floorDiv(blockX1, perUnit) + 1;
            int ci1 = Math.floorDiv(blockZ1, perUnit) + 1;
            FloatTensor slice = LocalTerrainProvider.getPipelineCoarse(ci0, cj0, ci1, cj1);
            return new CoarseMap(cj0, ci0, cj1 - cj0, ci1 - ci0, perUnit, slice.data);
        }

        /**
         * Classifies the coarse pixel containing the given block coordinates.
         * Returns {@link BiomeClassifier#PLAINS} for out-of-range lookups.
         */
        public short classifyPixel(int blockX, int blockZ) {
            int cj = Math.floorDiv(blockX, perUnit);
            int ci = Math.floorDiv(blockZ, perUnit);
            int j = cj - cj0;
            int i = ci - ci0;
            if (i < 0 || i >= height || j < 0 || j >= width) {
                return BiomeClassifier.PLAINS;
            }
            int plane = width * height;
            int pixel = i * width + j;
            float w = data[6 * plane + pixel];
            if (w <= MIN_WEIGHT) {
                return BiomeClassifier.PLAINS;
            }
            float elevSqrt = data[pixel] / w;
            float temp = data[2 * plane + pixel] / w;
            float tempStd = data[3 * plane + pixel] / w;
            float precip = data[4 * plane + pixel] / w;
            float precipStd = data[5 * plane + pixel] / w;
            return CoarseBiomeClassifier.classify(elevSqrt, temp, tempStd, precip, precipStd);
        }
    }
}
