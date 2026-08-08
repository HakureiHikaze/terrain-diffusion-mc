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
        int maxScale = maxScaleForLevel(serverLevel);

        WorldScaleSettingsState worldScaleSettingsState = savedDataStorage.computeIfAbsent(WorldScaleSettingsState.TYPE);

        if (!worldScaleSettingsState.hasExplicitScale()) {
            if (settingsFileExists) {
                LOG.warn("World scale settings file '{}' exists but the loaded state has no explicit scale; "
                        + "applying config/default scale. This usually means the saved data failed to load "
                        + "or was silently reset.", settingsFile);
            }
            Integer pendingScale = WorldScaleSelectionState.consumePendingScale();
            int resolvedScale = pendingScale != null ? pendingScale : TerrainDiffusionConfig.scale();
            worldScaleSettingsState.setScale(clampForLevel(resolvedScale, maxScale, serverLevel));
        }

        int loadedScale = clampScale(worldScaleSettingsState.getScale());
        if (loadedScale > maxScale) {
            LOG.warn("Persisted world scale {} exceeds the maximum {} supported by '{}' dimension "
                            + "(minY={}, height={}, sea_level={}); using {} at runtime without rewriting the save",
                    loadedScale, maxScale, serverLevel.dimension().identifier(),
                    serverLevel.dimensionType().minY(), serverLevel.dimensionType().height(),
                    TerrainDiffusionConfig.seaLevel(), maxScale);
            loadedScale = maxScale;
        }
        currentScale = loadedScale;
        LOG.info("World scale for '{}' loaded: {} (explicit={})",
                serverLevel.dimension().identifier(), currentScale, worldScaleSettingsState.hasExplicitScale());
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
        int clampedScale = clampForLevel(configuredScale, maxScaleForLevel(serverLevel), serverLevel);
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

    /**
     * Maximum terrain scale that fits inside the given dimension extent.
     *
     * <p>Elevation 0 m maps to {@code seaLevel}; the pipeline's tallest point is
     * 10000 m, so scale {@code s} reaches {@code seaLevel + floor(10000*s/30)}.
     * The limit is therefore {@code floor((topY - seaLevel) * 30 / 10000)}.
     * The lowest generated elevation (seaLevel - 97) is a fixed offset that the
     * mod's bundled dimensions satisfy; the top constraint is the binding one.
     */
    public static int maxScaleForDimension(int minY, int height, int seaLevel) {
        int topY = minY + height - 1;
        int maxScale = Math.floorDiv((topY - seaLevel) * 30, 10000);
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, maxScale));
    }

    private static int maxScaleForLevel(ServerLevel serverLevel) {
        DimensionType dimensionType = serverLevel.dimensionType();
        return maxScaleForDimension(dimensionType.minY(), dimensionType.height(), TerrainDiffusionConfig.seaLevel());
    }

    private static int clampForLevel(int configuredScale, int maxScale, ServerLevel serverLevel) {
        int clampedScale = Math.min(clampScale(configuredScale), maxScale);
        if (clampedScale != configuredScale) {
            LOG.warn("World scale {} exceeds the maximum {} supported by '{}' dimension; clamping to {}",
                    configuredScale, maxScale, serverLevel.dimension().identifier(), clampedScale);
        }
        return clampedScale;
    }
}
