package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.pipeline.SpawnSelector;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Provides the land spawn origin for Terrain Diffusion worlds.
 *
 * <p>26.3's {@code MinecraftServer.setInitialSpawn} derives the spawn chunk from
 * {@code ChunkGenerator.getOrigin} and pre-generates the surrounding 11x11 chunk
 * area. Returning the land position here keeps the vanilla spawn pipeline intact
 * (pre-generation included) instead of cancelling {@code setInitialSpawn}.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {

    @Inject(method = "getOrigin", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$landOrigin(RandomState randomState, CallbackInfoReturnable<ChunkPos> cir) {
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) (Object) this;
        if (!(generator.getBiomeSource() instanceof TerrainDiffusionBiomeSource)) {
            return;
        }
        BlockPos spawnPos = SpawnSelector.findSpawnBlockPos();
        cir.setReturnValue(ChunkPos.containing(spawnPos));
    }
}
