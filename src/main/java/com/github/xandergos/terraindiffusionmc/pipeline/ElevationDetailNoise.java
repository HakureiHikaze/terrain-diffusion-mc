package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;

/**
 * Scale-invariant elevation detail noise applied after bilinear upsampling.
 *
 * <p>Noise is sampled in model-native pixel coordinates ({@code blockCoord * nativePerBlock})
 * with fixed metre amplitudes, so the same physical location receives the same detail
 * regardless of the configured world scale. The amplitude is modulated by a Sobel
 * slope factor so detail concentrates on steep terrain; the slope threshold is also
 * expressed in native-pixel units so the weighting is scale-invariant.
 */
final class ElevationDetailNoise {

    static final float COARSE_AMPLITUDE_METERS = 100f;
    static final float FINE_AMPLITUDE_METERS = 70f;
    static final float SLOPE_NORM_METERS = 40f;

    private static final FastNoiseLite ELEV_NOISE_COARSE = makeFnl(99999, 1f / 24f, 3, 2f, 0.5f);
    private static final FastNoiseLite ELEV_NOISE_FINE = makeFnl(88888, 1f / 6f, 2, 2f, 0.6f);

    private ElevationDetailNoise() {
    }

    /**
     * Adds slope-weighted Perlin detail to a land elevation field.
     *
     * @param elevSmooth base elevation in metres, (H, W) row-major; not mutated
     * @param elevPadded edge-padded elevation for Sobel slope, (H+2, W+2) row-major
     * @param i0 world block Z of row 0
     * @param j0 world block X of column 0
     * @param H region height (rows)
     * @param W region width (columns)
     * @param nativePerBlock conversion from blocks to model-native pixels
     *     ({@code pixelSizeM / nativeResolution}, i.e. 1 / worldScale)
     * @return elevation array with detail noise added on land only; the input array is
     *     returned unchanged when {@code terrain.detail_noise.enabled} is false
     */
    static float[] apply(float[] elevSmooth, float[] elevPadded, int i0, int j0, int H, int W,
                         float nativePerBlock) {
        if (!TerrainDiffusionConfig.detailNoiseEnabled()) {
            return elevSmooth;
        }
        float[] slopeGradient = sobelGradient(elevPadded, H + 2, W + 2, H, W);
        float[] elevOut = elevSmooth.clone();
        float normFactor = SLOPE_NORM_METERS * nativePerBlock;

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float e = elevSmooth[idx];
                if (e < 0f) {
                    continue;
                }

                float grad = slopeGradient[idx];
                float sf = Math.min(1f, grad / normFactor);
                sf = sf * sf * (float) Math.sqrt(sf);

                float nx = (j0 + c) * nativePerBlock;
                float ny = (i0 + r) * nativePerBlock;
                elevOut[idx] = e
                        + ELEV_NOISE_COARSE.GetNoise(nx, ny) * COARSE_AMPLITUDE_METERS * sf
                        + ELEV_NOISE_FINE.GetNoise(nx, ny) * FINE_AMPLITUDE_METERS * sf;
            }
        }
        return elevOut;
    }

    private static float[] sobelGradient(float[] padded, int pH, int pW, int H, int W) {
        final float[] SOBEL_X = {-1, 0, 1, -2, 0, 2, -1, 0, 1};
        final float[] SOBEL_Y = {-1, -2, -1, 0, 0, 0, 1, 2, 1};
        float[] result = new float[H * W];
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                float dx = 0, dy = 0;
                for (int k = 0; k < 9; k++) {
                    float v = padded[(r + k / 3) * pW + (c + k % 3)];
                    dx += v * SOBEL_X[k];
                    dy += v * SOBEL_Y[k];
                }
                dx /= 8f;
                dy /= 8f;
                result[r * W + c] = (float) Math.sqrt(dx * dx + dy * dy);
            }
        }
        return result;
    }

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
}
