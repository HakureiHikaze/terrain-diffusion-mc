package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Carves winding river channels into a diffusion elevation field.
 *
 * <p>This is an integration-layer overlay: it does <em>not</em> change the diffusion model. The
 * model still produces the base terrain; rivers are added afterwards as a deterministic, stateless
 * function of world position and world seed. Because the channel at a given block depends only on
 * that block's coordinates (never on neighbouring tiles or generation order), the result stays
 * order-invariant and reproducible, consistent with the InfiniteDiffusion O(1) random-access design.
 *
 * <p>River centre-lines are the zero-contours of a smooth fractal-noise field: wherever the noise
 * is close to zero, the terrain is lowered into a channel that dips just below sea level (so the
 * world's sea fluid fills it), with smoothly tapered banks. Channels are only cut into land that is
 * above sea level and below a configurable altitude, so rivers never appear in the open ocean or on
 * high peaks. Elevation is expressed in metres, where 0 m corresponds to sea level.
 *
 * <p>Ported from upstream PR #207 (closed) by MaybeJustJames.
 */
public final class RiverCarver {

    private RiverCarver() {
    }

    /**
     * Lowers {@code elev} in place along river channels and returns a per-pixel mask flagging the
     * blocks that became river water (former land carved below sea level).
     *
     * @param elev              elevation in metres, row-major {@code (H, W)}; mutated in place
     * @param i0                world block Z of row 0
     * @param j0                world block X of column 0
     * @param H                 region height (rows / Z)
     * @param W                 region width (columns / X)
     * @param pixelSizeM        metres represented by one pixel (so frequency is scale-independent)
     * @param seed              world seed
     * @param frequency         river-network noise frequency (per metre); lower = rivers further apart
     * @param widthNoise        half-width of a channel in noise units; larger = wider rivers
     * @param depthMeters       how far below sea level a channel centre is cut
     * @param maxAltitudeMeters rivers are not cut into terrain above this elevation
     * @return river mask, {@code true} where a block was carved to below sea level
     */
    public static boolean[] carve(float[] elev, int i0, int j0, int H, int W, float pixelSizeM, long seed,
                                  float frequency, float widthNoise, float depthMeters, float maxAltitudeMeters) {
        boolean[] riverMask = new boolean[H * W];
        if (elev == null || widthNoise <= 0f || depthMeters <= 0f) {
            return riverMask;
        }

        FastNoiseLite riverNoise = new FastNoiseLite(mixSeed(seed));
        riverNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        riverNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        riverNoise.SetFractalOctaves(3);
        riverNoise.SetFrequency(frequency);

        float channelBottom = -depthMeters;

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float elevationMeters = elev[idx];

                // Only carve land that is above sea level and below the altitude cap.
                if (elevationMeters <= 0f || elevationMeters > maxAltitudeMeters) {
                    continue;
                }

                // Sample the river network in metres so widths/spacing are scale-independent.
                float worldXMeters = (j0 + c) * pixelSizeM;
                float worldZMeters = (i0 + r) * pixelSizeM;
                float distanceToCentreLine = Math.abs(riverNoise.GetNoise(worldXMeters, worldZMeters));
                if (distanceToCentreLine >= widthNoise) {
                    continue;
                }

                // Smoothly tapered channel: the centre-line is cut down to the channel floor (just
                // below sea level, so the world's sea fluid fills it), tapering flush with the banks.
                float t = 1f - distanceToCentreLine / widthNoise;
                float carveStrength = t * t * (3f - 2f * t);

                float carvedElevation = elevationMeters - carveStrength * (elevationMeters - channelBottom);
                if (carvedElevation < elevationMeters) {
                    elev[idx] = carvedElevation;
                }
                // Mark the wet part of the channel (below sea level) so it reads as a river biome.
                if (carvedElevation < 0f) {
                    riverMask[idx] = true;
                }
            }
        }

        return riverMask;
    }

    /**
     * Derives a stable, well-mixed int noise seed from the 64-bit world seed, offset so the river
     * network is decorrelated from the climate/elevation noises used elsewhere.
     */
    private static int mixSeed(long seed) {
        long z = (seed ^ 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (int) z ^ 0x52495645; // "RIVE"
    }
}
