package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainShapingTest {

    @Test
    void plateauMapMonotonic() {
        float lo = 600, hi = 1800, comp = 0.3f, steep = 2.5f;
        float prev = TerrainShaping.plateauMap(0, lo, hi, comp, steep);
        for (int e = 1; e < 3000; e++) {
            float curr = TerrainShaping.plateauMap(e, lo, hi, comp, steep);
            assertTrue(curr >= prev - 0.01f, "non-monotonic at " + e);
            prev = curr;
        }
    }

    @Test
    void plateauFlattensBand() {
        float lo = 600, hi = 1800, comp = 0.3f, steep = 2.5f;
        float midOut = TerrainShaping.plateauMap(1200, lo, hi, comp, steep);
        float loOut  = TerrainShaping.plateauMap(610, lo, hi, comp, steep);
        float hiOut  = TerrainShaping.plateauMap(1790, lo, hi, comp, steep);
        // The band should be compressed: mid-out is close to lo-out
        float bandSpan = hiOut - loOut;
        assertTrue(bandSpan < (hi - lo) * 0.5f, "band not compressed enough: " + bandSpan);
    }

    @Test
    void plateauCliffSteepensAbove() {
        float lo = 600, hi = 1800, comp = 0.3f, steep = 2.5f;
        float atHi  = TerrainShaping.plateauMap(1800, lo, hi, comp, steep);
        float above = TerrainShaping.plateauMap(1900, lo, hi, comp, steep);
        float diff = above - atHi;
        assertTrue(diff > 100, "cliff not steep enough: " + diff);
    }

    @Test
    void plateauDeterministic() {
        float lo = 600, hi = 1800, comp = 0.3f, steep = 2.5f;
        float a = TerrainShaping.plateauMap(800, lo, hi, comp, steep);
        float b = TerrainShaping.plateauMap(800, lo, hi, comp, steep);
        assertEquals(a, b, 0.0001f);
    }

    @Test
    void ridgesDoNotRaiseOcean() {
        int H = 5, W = 5;
        float[] elev = new float[H * W];
        // Half ocean (negative), half land (positive)
        for (int i = 0; i < H * W / 2; i++) elev[i] = -10f;
        for (int i = H * W / 2; i < H * W; i++) elev[i] = 100f;
        float[] copy = elev.clone();
        TerrainShaping.applyRidges(elev, 0, 0, H, W, 1f);
        for (int i = 0; i < H * W / 2; i++)
            assertEquals(copy[i], elev[i], 0.0001f, "ocean should be unchanged at " + i);
    }

    @Test
    void ridgesRaiseLand() {
        int H = 5, W = 5;
        float[] elev = new float[H * W];
        for (int i = 0; i < H * W; i++) elev[i] = 500f;
        TerrainShaping.applyRidges(elev, 0, 0, H, W, 1f);
        boolean anyRaised = false;
        for (int i = 0; i < H * W; i++)
            if (elev[i] > 500f) { anyRaised = true; break; }
        assertTrue(anyRaised);
    }

    @Test
    void ridgeDeltaIsScaleInvariant() {
        // Scale=1 at native pixel (3, 5); scale=2 at block (6, 10) maps to the same pixel.
        float[] scale1 = {500f};
        float[] scale2 = {500f};
        TerrainShaping.applyRidges(scale1, 3, 5, 1, 1, 1f);
        TerrainShaping.applyRidges(scale2, 6, 10, 1, 1, 0.5f);
        assertEquals(scale1[0], scale2[0], 1e-4f, "ridge delta must match across scales");
    }

    @Test
    void plateauDeltaIsScaleInvariant() {
        float[] scale1 = {900f};
        float[] scale2 = {900f};
        TerrainShaping.applyPlateaus(scale1, 3, 5, 1, 1, 1f);
        TerrainShaping.applyPlateaus(scale2, 6, 10, 1, 1, 0.5f);
        assertEquals(scale1[0], scale2[0], 1e-4f, "plateau delta must match across scales");
    }
}
