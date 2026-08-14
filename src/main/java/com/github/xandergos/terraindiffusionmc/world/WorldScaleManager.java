package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import net.minecraft.server.level.ServerLevel;

/**
 * Runtime access for world-scoped terrain scale.
 */
public final class WorldScaleManager {
    public static final int DEFAULT_SCALE = TerrainDiffusionConfig.DEFAULT_WORLD_SCALE;
    private static final int MIN_SCALE = TerrainDiffusionConfig.MIN_WORLD_SCALE;
    public static final int MAX_SCALE = TerrainDiffusionConfig.MAX_WORLD_SCALE;

    private static volatile int currentScale = DEFAULT_SCALE;

    private WorldScaleManager() {
    }

    /**
     * Loads or creates per-world scale settings and sets the active runtime value.
     *
     * <p>If the world has no explicit stored scale yet, this applies pending
     * world-creation selection when present, otherwise falls back to {@value #DEFAULT_SCALE}.
     */
    public static void initializeForWorld(ServerLevel serverWorld) {
        WorldScaleSettingsState worldScaleSettingsState = serverWorld.getDataStorage()
                .computeIfAbsent(WorldScaleSettingsState.TYPE);

        if (!worldScaleSettingsState.hasExplicitScale()) {
            Integer pendingScale = WorldScaleSelectionState.consumePendingScale();
            int resolvedScale = pendingScale != null ? pendingScale : TerrainDiffusionConfig.worldScale();
            worldScaleSettingsState.setScale(resolvedScale);
        }

        currentScale = clampScale(worldScaleSettingsState.getScale());
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
    public static void setCurrentScale(ServerLevel serverWorld, int configuredScale) {
        int clampedScale = clampScale(configuredScale);
        WorldScaleSettingsState worldScaleSettingsState = serverWorld.getDataStorage()
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
