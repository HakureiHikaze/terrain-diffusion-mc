package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiverCarverTest {

    @Test
    void carveAlongMaskLowersCentreEligibleLand() {
        int H = 3, W = 3;
        float[] elev = {20, 20, 20, 20, 15, 20, 20, 20, 20};
        boolean[] mask = {false, false, false, false, true, false, false, false, false};

        boolean[] wet = RiverCarver.carveAlongMask(elev, mask, H, W,
                16f, 40f, 0, 1);

        // Centre pixel was carved below sea level
        assertTrue(elev[4] < 0f);
        assertTrue(wet[4]);
        // Non-river pixels unchanged
        assertEquals(20f, elev[0], 0.001f);
        assertFalse(wet[0]);
    }

    @Test
    void carveAlongMaskRespectsMaxAltitude() {
        int H = 3, W = 3;
        float[] elev = {20, 20, 20, 20, 100, 20, 20, 20, 20};
        boolean[] mask = {false, false, false, false, true, false, false, false, false};

        boolean[] wet = RiverCarver.carveAlongMask(elev, mask, H, W,
                16f, 40f, 0, 1);

        // Above max altitude → NOT carved
        assertEquals(100f, elev[4], 0.001f);
        assertFalse(wet[4]);
    }

    @Test
    void carveAlongMaskIgnoresOcean() {
        int H = 3, W = 3;
        float[] elev = {20, 20, 20, 20, -1, 20, 20, 20, 20};
        boolean[] mask = {false, false, false, false, true, false, false, false, false};

        boolean[] wet = RiverCarver.carveAlongMask(elev, mask, H, W,
                16f, 40f, 0, 1);

        // Already ocean → NOT carved
        assertEquals(-1f, elev[4], 0.001f);
        assertFalse(wet[4]);
    }

    @Test
    void bankSmoothingRoundsDilateChannel() {
        int H = 5, W = 5;
        float[] elev = new float[25];
        for (int i = 0; i < 25; i++) elev[i] = 20f;
        boolean[] mask = new boolean[25];
        mask[12] = true; // centre only

        // bankSmoothingRounds=1 → centre + 1 ring
        RiverCarver.carveAlongMask(elev, mask, H, W, 16f, 40f, 1, 1);

        // Centre carved deepest
        assertTrue(elev[12] < 0f);
        // Neighbours carved less deep (but may or may not reach below sea level)
        boolean anyNeighbourCarved = false;
        int[] neighbours = {6, 7, 8, 11, 13, 16, 17, 18};
        for (int n : neighbours) {
            if (elev[n] < 20f) anyNeighbourCarved = true;
        }
        assertTrue(anyNeighbourCarved);
    }

    @Test
    void nullInputsReturnEmptyMask() {
        boolean[] wet = RiverCarver.carveAlongMask(null, null, 10, 10, 16f, 40f, 0, 1);
        assertEquals(100, wet.length);
        for (boolean w : wet) assertFalse(w);
    }

    @Test
    void bankSmoothingRingWidthScalesWithWorldScale() {
        int H = 7, W = 7;
        float[] elevScale1 = new float[H * W];
        float[] elevScale2 = new float[H * W];
        for (int i = 0; i < H * W; i++) {
            elevScale1[i] = 20f;
            elevScale2[i] = 20f;
        }
        boolean[] mask = new boolean[H * W];
        mask[3 * W + 3] = true;

        RiverCarver.carveAlongMask(elevScale1, mask.clone(), H, W, 16f, 40f, 1, 1);
        RiverCarver.carveAlongMask(elevScale2, mask.clone(), H, W, 16f, 40f, 1, 2);

        int carvedScale1 = countCarved(elevScale1);
        int carvedScale2 = countCarved(elevScale2);
        // Scale=2 expands each ring by 2 blocks: one ring covers a 5x5 area instead of 3x3.
        assertEquals(9, carvedScale1, "scale=1 ring must cover 3x3 blocks");
        assertEquals(25, carvedScale2, "scale=2 ring must cover 5x5 blocks");
    }

    private static int countCarved(float[] elev) {
        int count = 0;
        for (float e : elev) {
            if (e < 20f) count++;
        }
        return count;
    }
}
