package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.SpawnSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    private static final Logger LOG = LoggerFactory.getLogger(MinecraftServerMixin.class);

    @Shadow
    private void updateEffectiveRespawnData() {
        throw new AssertionError("Shadowed");
    }

    @Shadow
    public void updateMobSpawningFlags() {
        throw new AssertionError("Shadowed");
    }

    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void overrideWorldSpawn(ServerLevel level, ServerLevelData levelData,
                                           boolean spawnBonusChest, boolean isDebug,
                                           LevelLoadListener levelLoadListener, CallbackInfo ci) {
        LOG.info("MIXIN PROBE: setInitialSpawn HEAD hit on dimension {}", level.dimension());
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        BlockPos spawnPos = SpawnSelector.findSpawnBlockPos();
        levelData.setSpawn(LevelData.RespawnData.of(Level.OVERWORLD, spawnPos, 0f, 0f));
        // Cancel the rest of vanilla setInitialSpawn: its ClimateSampler.findSpawnPosition()
        // and 11x11 PlayerSpawnFinder search sample the density function over a huge area,
        // which (with this mod's density function) triggers tens of thousands of diffusion
        // tile generations before the server becomes joinable. Our spawn is already set, and
        // the join-time PrepareSpawnTask generates the 7x7-chunk spawn area on demand.
        ci.cancel();
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void skipInitialChunkPreload(CallbackInfo ci) {
        boolean skip = TerrainDiffusionConfig.skipInitialChunkPreload();
        LOG.info("MIXIN PROBE: prepareLevels HEAD hit, worldgen.skip_initial_chunk_preload={}", skip);
        if (skip) {
            LOG.info("Skipping initial chunk preload (worldgen.skip_initial_chunk_preload=true): "
                    + "server starts immediately, terrain generates on demand");
            updateMobSpawningFlags();
            updateEffectiveRespawnData();
            ci.cancel();
        }
    }
}
