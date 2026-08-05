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
     * Carves river channels along a precomputed path mask (e.g. D8 flow-accumulation
     * paths from {@link RiverDetector}). The mask centre-line is cut down to the
     * channel floor (below sea level so the world's sea fluid fills it); each
     * additional {@code bankSmoothingRounds} dilates the channel by one ring with a
     * progressively shallower cut for smooth tapered banks. Channels are only cut
     * into land above sea level and below {@code maxAltitudeMeters}.
     *
     * @param elev                elevation in metres, row-major {@code (H, W)}; mutated in place
     * @param riverMask           per-pixel river path mask, length H*W
     * @param H                   height (rows)
     * @param W                   width (columns)
     * @param depthMeters         how far below sea level a channel centre is cut
     * @param maxAltitudeMeters   channels are not cut into terrain above this elevation
     * @param bankSmoothingRounds how many dilation rings to add for tapered banks (0 = centre-line only)
     * @return wet mask, {@code true} where a block was carved to below sea level
     */
    public static boolean[] carveAlongMask(float[] elev, boolean[] riverMask, int H, int W,
                                           float depthMeters, float maxAltitudeMeters, int bankSmoothingRounds) {
        boolean[] wet = new boolean[H * W];
        if (elev == null || riverMask == null || depthMeters <= 0f) {
            return wet;
        }
        int N = H * W;
        boolean[] processed = new boolean[N];
        boolean[] current = riverMask;
        for (int ring = 0; ring <= bankSmoothingRounds; ring++) {
            float weight = (float) (bankSmoothingRounds - ring + 1) / (float) (bankSmoothingRounds + 1);
            for (int i = 0; i < N; i++) {
                if (!current[i] || processed[i]) continue;
                processed[i] = true;
                float e = elev[i];
                if (e <= 0f || e > maxAltitudeMeters) continue;
                float carved = e - weight * (e + depthMeters);
                if (carved < e) {
                    elev[i] = carved;
                }
                if (carved < 0f) {
                    wet[i] = true;
                }
            }
            if (ring < bankSmoothingRounds) {
                current = dilate(current, H, W);
            }
        }
        return wet;
    }

    /** 8-neighbour dilation of a boolean 2D mask. */
    private static boolean[] dilate(boolean[] mask, int H, int W) {
        boolean[] out = new boolean[H * W];
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                if (!mask[r * W + c]) continue;
                int r0 = Math.max(0, r - 1), r1 = Math.min(H - 1, r + 1);
                int c0 = Math.max(0, c - 1), c1 = Math.min(W - 1, c + 1);
                for (int nr = r0; nr <= r1; nr++) {
                    for (int nc = c0; nc <= c1; nc++) {
                        out[nr * W + nc] = true;
                    }
                }
            }
        }
        return out;
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
