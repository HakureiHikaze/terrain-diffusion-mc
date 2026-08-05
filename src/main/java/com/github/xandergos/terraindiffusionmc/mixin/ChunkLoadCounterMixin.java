package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import net.minecraft.server.level.ChunkLoadCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When {@code worldgen.skip_initial_chunk_preload} is enabled, reports zero pending
 * chunks so the 26.x startup "load initial chunks" wait (MinecraftServer#prepareLevels)
 * finishes immediately and the server becomes joinable without pre-generating a huge
 * spawn area (each diffusion tile takes seconds on a mid-range GPU).
 *
 * <p>This injects a different class than MinecraftServerMixin as a belt-and-braces
 * approach: whichever injection point applies, the startup preload is skipped.
 */
@Mixin(ChunkLoadCounter.class)
public class ChunkLoadCounterMixin {
    private static final Logger LOG = LoggerFactory.getLogger(ChunkLoadCounterMixin.class);

    @Inject(method = "pendingChunks", at = @At("HEAD"), cancellable = true)
    private void skipInitialChunkPreload(CallbackInfoReturnable<Integer> cir) {
        if (TerrainDiffusionConfig.skipInitialChunkPreload()) {
            LOG.info("MIXIN PROBE: ChunkLoadCounter.pendingChunks hit, reporting 0 pending chunks");
            cir.setReturnValue(0);
        }
    }
}
