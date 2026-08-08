package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runtime access for world-scoped terrain scale.
 */
public final class WorldScaleManager {
    private static final Logger LOG = LoggerFactory.getLogger(WorldScaleManager.class);

    public static final int DEFAULT_SCALE = 2;
    private static final int MIN_SCALE = 1;
    public static final int MAX_SCALE = 15;

    private static volatile int currentScale = DEFAULT_SCALE;

    private WorldScaleManager() {
    }

    /**
     * Loads or creates per-world scale settings and sets the active runtime value.
     *
     * <p>If the world has no explicit stored scale yet, this applies pending
     * world-creation selection when present, otherwise falls back to {@value #DEFAULT_SCALE}.
     */
    public static void initializeForWorld(ServerLevel serverLevel) {
        SavedDataStorage savedDataStorage = serverLevel.getChunkSource().getDataStorage();
        // ServerChunkCache stores per-dimension saved data under
        // <world>/dimensions/<namespace>/<path>/data (DimensionType.getStorageFolder),
        // not the server-level <world>/data folder.
        Path dimensionDataFolder = DimensionType.getStorageFolder(
                        serverLevel.dimension(),
                        serverLevel.getServer().getWorldPath(LevelResource.ROOT))
                .resolve("data");
        Path settingsFile = WorldScaleSettingsState.TYPE.id()
                .withSuffix(".dat")
                .resolveAgainst(dimensionDataFolder);
        boolean settingsFileExists = Files.exists(settingsFile);

        WorldScaleSettingsState worldScaleSettingsState = savedDataStorage.computeIfAbsent(WorldScaleSettingsState.TYPE);

        if (!worldScaleSettingsState.hasExplicitScale()) {
            if (settingsFileExists) {
                LOG.warn("World scale settings file '{}' exists but the loaded state has no explicit scale; "
                        + "applying config/default scale. This usually means the saved data failed to load "
                        + "or was silently reset.", settingsFile);
            }
            Integer pendingScale = WorldScaleSelectionState.consumePendingScale();
            int resolvedScale = pendingScale != null ? pendingScale : TerrainDiffusionConfig.scale();
            worldScaleSettingsState.setScale(resolvedScale);
        }

        currentScale = clampScale(worldScaleSettingsState.getScale());
        LOG.info("World scale for '{}' loaded: {} (explicit={})",
                serverLevel.dimension().location(), currentScale, worldScaleSettingsState.hasExplicitScale());
    }

    /**
     * Returns the currently active world scale.
     */
    public static int getCurrentScale() {
        return currentScale;
    }

    /**
     * Updates world scale for the currently loaded world and persists it immediately.
     */
    public static void setCurrentScale(ServerLevel serverLevel, int configuredScale) {
        int clampedScale = clampScale(configuredScale);
        WorldScaleSettingsState worldScaleSettingsState = serverLevel.getChunkSource()
                .getDataStorage()
                .computeIfAbsent(WorldScaleSettingsState.TYPE);
        worldScaleSettingsState.setScale(clampedScale);
        currentScale = clampedScale;
    }

    /**
     * Clamps world scale to supported runtime bounds.
     */
    public static int clampScale(int configuredScale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, configuredScale));
    }
}
