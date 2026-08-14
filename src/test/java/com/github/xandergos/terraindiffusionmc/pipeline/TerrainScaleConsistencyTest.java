package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainScaleConsistencyTest {

    @Test
    void detailNoiseMatchesNativeFieldAcrossScales() {
        // Scale=1: native pixel (1,1), gradient 10 m per native pixel.
        float[] elevScale1 = {100f};
        float[] paddedScale1 = {
            0f, 10f, 20f,
            10f, 20f, 30f,
            20f, 30f, 40f
        };

        // Scale=2: block (2,2) maps to the same native pixel; gradient 5 m per block.
        float[] elevScale2 = {100f};
        float[] paddedScale2 = {
            0f, 5f, 10f,
            5f, 10f, 15f,
            10f, 15f, 20f
        };

        float[] outScale1 = ElevationDetailNoise.apply(elevScale1, paddedScale1, 1, 1, 1, 1, 1f);
        float[] outScale2 = ElevationDetailNoise.apply(elevScale2, paddedScale2, 2, 2, 1, 1, 0.5f);

        assertEquals(outScale1[0] - elevScale1[0], outScale2[0] - elevScale2[0], 1e-3f,
                "detail noise delta must match across scales at the same native pixel");
    }

    @Test
    void detailNoiseLeavesOceanUnchanged() {
        float[] elev = {-10f};
        float[] padded = new float[9];
        float[] out = ElevationDetailNoise.apply(elev, padded, 0, 0, 1, 1, 1f);
        assertEquals(-10f, out[0], 0f);
    }

    @Test
    void amplitudeConstantsAreFixedMetres() {
        assertEquals(100f, ElevationDetailNoise.COARSE_AMPLITUDE_METERS, 0f);
        assertEquals(70f, ElevationDetailNoise.FINE_AMPLITUDE_METERS, 0f);
        assertEquals(40f, ElevationDetailNoise.SLOPE_NORM_METERS, 0f);
    }
}
