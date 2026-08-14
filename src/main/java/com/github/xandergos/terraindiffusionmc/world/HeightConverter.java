package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipelineModelConfig;

public class HeightConverter {
    private static final int SEA_LEVEL = 63;
    private static final short MAX_PIPELINE_METERS = 10_000;

    public static int convertToMinecraftHeight(short meters) {
        return convertToMinecraftHeight(meters, WorldScaleManager.getCurrentScale());
    }

    public static int convertToMinecraftHeight(short meters, int configuredScale) {
        return convertToMinecraftHeight(meters, configuredScale, WorldPipelineModelConfig.nativeResolution());
    }

    /**
     * Converts pipeline elevation in metres to a Minecraft block Y coordinate.
     *
     * <p>Positive elevations scale linearly with the world scale. Negative elevations
     * intentionally keep the bounded sqrt compression without a scale factor so that
     * deep ocean floors stay within the world's {@code min_y=-64} build limit.
     *
     * @param meters          pipeline elevation in metres
     * @param configuredScale world scale (blocks per native pixel)
     * @param nativeResolution metres per native pixel
     * @return Minecraft block Y coordinate
     */
    static int convertToMinecraftHeight(short meters, int configuredScale, float nativeResolution) {
        int baseY;
        float resolution = nativeResolution / WorldScaleManager.clampScale(configuredScale);

        if (meters >= 0) {
            baseY = (int) (meters / resolution);
        } else {
            baseY = (int) (-Math.sqrt(Math.abs(meters) + 10) + Math.sqrt(10.0)) - 1;
        }

        return baseY + SEA_LEVEL;
    }

    /**
     * Returns the highest generated block Y expected from pipeline output for a given scale.
     */
    public static int getMaxGeneratedYForScale(int configuredScale) {
        return convertToMinecraftHeight(MAX_PIPELINE_METERS, configuredScale);
    }
}
