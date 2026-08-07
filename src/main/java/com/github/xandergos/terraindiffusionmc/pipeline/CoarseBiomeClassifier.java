package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Simplified biome classification from coarse-map pixels.
 *
 * <p>Used for structure placement / biome-location searches where an exact
 * full-resolution classification would require expensive full-pipeline terrain
 * inference. Rules mirror the temperature/elevation/precipitation bands of
 * {@link BiomeClassifier} without slope/growing-season detail.
 */
public final class CoarseBiomeClassifier {

    private CoarseBiomeClassifier() {
    }

    /**
     * Classify a single coarse-map pixel.
     *
     * @param elevSqrt   signed sqrt of elevation in meters (coarse channel 0)
     * @param temp       temperature in Celsius (coarse channel 2)
     * @param tempStd    temperature std (coarse channel 3, unused here)
     * @param precip     precipitation in mm (coarse channel 4)
     * @param precipStd  precipitation std (coarse channel 5, unused here)
     * @return biome id matching {@link BiomeClassifier} id constants
     */
    public static short classify(float elevSqrt, float temp, float tempStd, float precip, float precipStd) {
        float elev = Math.signum(elevSqrt) * elevSqrt * elevSqrt;
        boolean ocean = elev < 0f;
        float alt = Math.max(0f, elev);
        boolean mountains = alt > 2500f;
        boolean lowland = alt < 200f;

        boolean frozen = temp < -5f;
        boolean cold = temp >= -5f && temp < 5f;
        boolean cool = temp >= 5f && temp < 12f;
        boolean temperate = temp >= 12f && temp < 20f;
        boolean warm = temp >= 20f && temp < 26f;
        boolean hot = temp >= 26f;

        boolean wet = precip > 700f;
        boolean dry = precip < 350f;

        if (ocean) {
            if (frozen) return BiomeClassifier.FROZEN_OCEAN;
            if (cold) return BiomeClassifier.COLD_OCEAN;
            if (warm || hot) return BiomeClassifier.WARM_OCEAN;
            return BiomeClassifier.OCEAN;
        }

        if (mountains) {
            if (frozen) return BiomeClassifier.FROZEN_PEAKS;
            if (cold) return BiomeClassifier.SNOWY_SLOPES;
            if (dry) return BiomeClassifier.STONY_PEAKS;
            if (wet) return BiomeClassifier.TAIGA;
            return BiomeClassifier.GROVE;
        }

        if (frozen) return dry ? BiomeClassifier.SNOWY_PLAINS : BiomeClassifier.SNOWY_TAIGA;
        if (cold) return wet ? BiomeClassifier.TAIGA : (dry ? BiomeClassifier.GROVE : BiomeClassifier.FOREST);
        if (cool) return wet ? BiomeClassifier.TAIGA : BiomeClassifier.FOREST;
        if (temperate) return dry ? (lowland ? BiomeClassifier.SAVANNA : BiomeClassifier.PLAINS) : BiomeClassifier.FOREST;
        if (warm) return dry ? BiomeClassifier.SAVANNA : (lowland ? BiomeClassifier.SWAMP : BiomeClassifier.JUNGLE);
        return dry ? BiomeClassifier.DESERT : BiomeClassifier.JUNGLE;
    }
}
