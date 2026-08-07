package com.github.xandergos.terraindiffusionmc;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal bootstrap for the 26.3-fabric environment bring-up.
 *
 * <p>The full terrain-diffusion-mc implementation (1.21.11 / yarn) is parked in
 * {@code legacy-src/} and will be ported to the 26.3 (official names) API on top
 * of this branch. This class only proves that the toolchain (Loom 1.17, loader
 * 0.19.3, Fabric API 0.156.3+26.3, Minecraft 26.3-snapshot-7, Java 25) builds
 * and that the client runs.
 */
public class TerrainDiffusionMc implements ModInitializer {
    public static final String MOD_ID = "terrain-diffusion-mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    @Override
    public void onInitialize() {
        LOG.info("terrain-diffusion-mc (26.3-fabric stub) initialized");
    }
}
