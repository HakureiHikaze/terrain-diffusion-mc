package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.github.xandergos.terraindiffusionmc.world.CoarseBiomeSearch;
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
 *
 * <p>Search radius and iteration order now match vanilla: {@code findBiomeHorizontal}
 * keeps the quart-grid square at the caller-provided radius (no 16x amplification)
 * with reservoir sampling, and {@code findClosestBiome3d} keeps vanilla's
 * nearest-first spiral. The coarse map is fetched once for the whole search
 * domain; result coordinates are quart-snapped like vanilla.
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
        cir.setReturnValue(CoarseBiomeSearch.findBiomeHorizontal(
                source, originX, originY, originZ, searchRadius, allowed, random));
    }

    @Inject(method = "findClosestBiome3d", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$coarseFindClosestBiome3d(
            BlockPos origin, int searchRadius, int sampleResolutionHorizontal, int sampleResolutionVertical,
            Predicate<Holder<Biome>> allowed, Climate.Sampler sampler, LevelReader level,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Biome>>> cir) {
        if (!((Object) this instanceof TerrainDiffusionBiomeSource source)) {
            return;
        }
        cir.setReturnValue(CoarseBiomeSearch.findClosestBiome3d(
                source, origin, searchRadius, sampleResolutionHorizontal, sampleResolutionVertical,
                allowed, level));
    }
}
