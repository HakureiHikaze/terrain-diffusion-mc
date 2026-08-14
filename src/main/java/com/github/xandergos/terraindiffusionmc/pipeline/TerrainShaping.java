package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;

public final class TerrainShaping {

    private static final FastNoiseLite ELEV_NOISE_RIDGE = makeFnl(77777, 1f / 48f, 3, 2f, 0.5f);
    private static final FastNoiseLite PLATEAU_NOISE   = makeFnl(66666, 1f / 800f, 2, 2f, 0.5f);

    private static FastNoiseLite makeFnl(int seed, float freq, int oct, float lac, float gain) {
        FastNoiseLite fnl = new FastNoiseLite(seed);
        fnl.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        fnl.SetFrequency(freq);
        fnl.SetFractalType(FastNoiseLite.FractalType.FBm);
        fnl.SetFractalOctaves(oct);
        fnl.SetFractalLacunarity(lac);
        fnl.SetFractalGain(gain);
        return fnl;
    }

    private TerrainShaping() {}

    /**
     * Applies ridge and plateau shaping in model-native pixel coordinates.
     *
     * @param nativePerBlock conversion from block coordinates to model-native pixels
     *     ({@code pixelSizeM / nativeResolution}, i.e. 1 / worldScale)
     */
    public static void apply(float[] elev, int i0, int j0, int H, int W, float nativePerBlock) {
        if (TerrainDiffusionConfig.ridgesEnabled()) {
            applyRidges(elev, i0, j0, H, W, nativePerBlock);
        }
        if (TerrainDiffusionConfig.plateausEnabled()) {
            applyPlateaus(elev, i0, j0, H, W, nativePerBlock);
        }
    }

    static void applyRidges(float[] elev, int i0, int j0, int H, int W, float nativePerBlock) {
        float amp = TerrainDiffusionConfig.ridgeAmplitude();
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float e = elev[idx];
                if (e < 0f) continue;

                float sf = Math.min(1f, Math.max(0f, e / 1500f));
                float nx = (j0 + c) * nativePerBlock;
                float ny = (i0 + r) * nativePerBlock;
                elev[idx] = e + Math.abs(ELEV_NOISE_RIDGE.GetNoise(nx, ny)) * amp * sf;
            }
        }
    }

    static void applyPlateaus(float[] elev, int i0, int j0, int H, int W, float nativePerBlock) {
        float minBase = TerrainDiffusionConfig.plateauMin();
        float maxBase = TerrainDiffusionConfig.plateauMax();
        float compression = TerrainDiffusionConfig.plateauCompression();
        float cliffSteepness = TerrainDiffusionConfig.plateauCliffSteepness();
        float variability = TerrainDiffusionConfig.plateauVariability();

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float e = elev[idx];
                if (e < 0f) continue;

                float n = PLATEAU_NOISE.GetNoise(
                        (j0 + c) * nativePerBlock, (i0 + r) * nativePerBlock);
                float shift = n * variability;
                float lo = minBase + shift;
                float hi = maxBase + shift;
                if (hi <= lo) hi = lo + 1f;

                elev[idx] = plateauMap(e, lo, hi, compression, cliffSteepness);
            }
        }
    }

    static float plateauMap(float elev, float lo, float hi, float compression, float steepness) {
        float range = hi - lo;
        float plateauHeight = lo + range * compression;
        float cliffSlope = steepness;

        if (elev <= lo) {
            return elev;
        }
        if (elev <= hi) {
            float t = (elev - lo) / range;
            float s = smoothstep(t);
            return lo + s * (plateauHeight - lo);
        }
        return plateauHeight + (elev - hi) * cliffSlope;
    }

    private static float smoothstep(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }
}
