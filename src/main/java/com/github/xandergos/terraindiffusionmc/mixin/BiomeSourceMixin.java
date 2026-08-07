package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Routes biome-location searches (structure placement, /locate biome) for
 * Terrain Diffusion worlds through the coarse-map classifier instead of the
 * full terrain pipeline.
 *
 * <p>Vanilla's structure initialization (e.g. stronghold rings) probes biome
 * sources over a large area; without this, every probe triggered a complete
 * 256x256 tile inference, generating terrain dozens of kilometers away from the
 * spawn point.
 */
@Mixin(BiomeSource.class)
public abstract class BiomeSourceMixin {

    @Inject(method = "findBiomeHorizontal", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$coarseFindBiomeHorizontal(
            int originX, int originY, int originZ, int searchRadius,
            Predicate<Holder<Biome>> allowed, RandomSource random,
            Climate.Sampler sampler, CallbackInfoReturnable<Pair<BlockPos, Holder<Biome>>> cir) {
        if (!((Object) this instanceof TerrainDiffusionBiomeSource source)) {
            return;
        }
        cir.setReturnValue(coarseSearch(source, originX, originZ, searchRadius * 16, allowed));
    }

    @Inject(method = "findClosestBiome3d", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$coarseFindClosestBiome3d(
            BlockPos origin, int searchRadius, int sampleResolutionHorizontal, int sampleResolutionVertical,
            Predicate<Holder<Biome>> allowed, Climate.Sampler sampler, LevelReader level,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Biome>>> cir) {
        if (!((Object) this instanceof TerrainDiffusionBiomeSource source)) {
            return;
        }
        cir.setReturnValue(coarseSearch(source, origin.getX(), origin.getZ(), searchRadius, allowed));
    }

    /**
     * Spiral search over the coarse map (one unit per coarse pixel), returning
     * the first pixel whose coarse classification satisfies {@code allowed}.
     * Returns null (no result) when nothing matches within the radius, without
     * touching the full terrain pipeline.
     */
    private static Pair<BlockPos, Holder<Biome>> coarseSearch(
            TerrainDiffusionBiomeSource source, int originX, int originZ, int radiusBlocks, Predicate<Holder<Biome>> allowed) {
        int perUnit = LocalTerrainProvider.blocksPerCoarseUnit();
        int ci0 = Math.floorDiv(originZ - radiusBlocks, perUnit);
        int cj0 = Math.floorDiv(originX - radiusBlocks, perUnit);
        int ci1 = Math.floorDiv(originZ + radiusBlocks, perUnit);
        int cj1 = Math.floorDiv(originX + radiusBlocks, perUnit);
        int centerCi = Math.floorDiv(originZ, perUnit);
        int centerCj = Math.floorDiv(originX, perUnit);
        int maxDist = Math.max(Math.abs(ci1 - centerCi), Math.abs(cj1 - centerCj));

        for (int dist = 0; dist <= maxDist; dist++) {
            for (int dc = -dist; dc <= dist; dc++) {
                for (int dr = -dist; dr <= dist; dr++) {
                    if (Math.abs(dc) != dist && Math.abs(dr) != dist) continue;
                    int ci = centerCi + dr;
                    int cj = centerCj + dc;
                    if (ci < ci0 || ci > ci1 || cj < cj0 || cj > cj1) continue;
                    short id = LocalTerrainProvider.classifyCoarseBiome(cj * perUnit, ci * perUnit);
                    Holder<Biome> holder = source.getBiomeHolderById(id);
                    if (holder != null && allowed.test(holder)) {
                        return Pair.of(new BlockPos(cj * perUnit, 0, ci * perUnit), holder);
                    }
                }
            }
        }
        return null;
    }
}
