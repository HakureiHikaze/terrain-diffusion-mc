package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WonderGeneratorTest {

    @Test
    void wondersDeterministic() {
        int H = 32, W = 32;
        float[] a = new float[H * W], b = new float[H * W];
        for (int i = 0; i < H * W; i++) { a[i] = 50f; b[i] = 50f; }
        short[] taiga = new short[H * W];
        for (int i = 0; i < H * W; i++) taiga[i] = BiomeClassifier.TAIGA;

        WonderGenerator.apply(a, taiga, 0, 0, H, W, 90f, 1f, 12345L);
        WonderGenerator.apply(b, taiga, 0, 0, H, W, 90f, 1f, 12345L);
        for (int i = 0; i < H * W; i++)
            assertEquals(a[i], b[i], 0.0001f, "determinism failed at " + i);
    }

    @Test
    void spireGateRejectsNonTaiga() {
        int H = 16, W = 16;
        float[] elev = new float[H * W];
        for (int i = 0; i < H * W; i++) elev[i] = 50f;
        short[] desert = new short[H * W];
        for (int i = 0; i < H * W; i++) desert[i] = BiomeClassifier.DESERT;

        WonderGenerator.apply(elev, desert, 0, 0, H, W, 90f, 1f, 42L);
        for (int i = 0; i < H * W; i++)
            assertEquals(50f, elev[i], 0.0001f, "desert biome should block spire, got raise at " + i);
    }

    @Test
    void calderaAppliedWithoutCrash() {
        int H = 32, W = 32;
        float[] elev = new float[H * W];
        for (int i = 0; i < H * W; i++) elev[i] = 100f;
        short[] badlands = new short[H * W];
        for (int i = 0; i < H * W; i++) badlands[i] = BiomeClassifier.BADLANDS;

        WonderGenerator.apply(elev, badlands, 0, 0, H, W, 90f, 1f, 999L);
        // Wonders are rare — may or may not appear; just verify no crash / no NaN
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < H * W; i++) {
            assertFalse(Float.isNaN(elev[i]), "NaN at " + i);
            assertTrue(elev[i] > -1000f && elev[i] < 5000f, "extreme value at " + i);
        }
    }

    @Test
    void oceanCellsUntouched() {
        int H = 16, W = 16;
        float[] elev = new float[H * W];
        for (int i = 0; i < H * W; i++) elev[i] = -20f;
        short[] taiga = new short[H * W];
        for (int i = 0; i < H * W; i++) taiga[i] = BiomeClassifier.TAIGA;

        WonderGenerator.apply(elev, taiga, 0, 0, H, W, 90f, 1f, 42L);
        for (int i = 0; i < H * W; i++)
            assertEquals(-20f, elev[i], 0.0001f, "ocean cell modified at " + i);
    }

    @Test
    void coneKernelZeroAtRadius() {
        float r = 5f;
        assertEquals(0f, WonderGenerator.coneKernel(0, 0, 0, (int)r + 1, r, 100f), 0.1f);
    }

    @Test
    void mesaKernelFlatOnPlateau() {
        float r = 8f, h = 50f;
        assertEquals(h, WonderGenerator.mesaKernel(0, 0, 0, 0, r, h), 0.1f);
        assertEquals(h, WonderGenerator.mesaKernel(0, 6, 0, 0, r, h), 0.1f);
        assertEquals(0f, WonderGenerator.mesaKernel(0, (int)(r * 1.3f), 0, 0, r, h), 0.1f);
    }

    @Test
    void gorgeWidthKeepsPhysicalSizeAcrossScales() {
        float nativeResolution = 30f;
        int[] scales = {1, 2, 4, 6};
        for (int s : scales) {
            float nativePerBlock = 1f / s;
            float widthPx = WonderGenerator.gorgeWidthPixels(nativePerBlock);
            float physicalWidthM = widthPx * (nativeResolution / s);
            assertEquals(5f * nativeResolution, physicalWidthM, 1e-3f,
                    "gorge physical width must be scale-invariant at s=" + s);
        }
    }

    @Test
    void pillarRadiusKeepsPhysicalSizeAcrossScales() {
        float nativeResolution = 30f;
        float noise = 0.5f;
        int[] scales = {1, 2, 4, 6};
        for (int s : scales) {
            float nativePerBlock = 1f / s;
            float radiusPx = WonderGenerator.pillarRadiusPixels(noise, nativePerBlock);
            float physicalRadiusM = radiusPx * (nativeResolution / s);
            assertEquals(5.5f * nativeResolution, physicalRadiusM, 1e-3f,
                    "pillar physical radius must be scale-invariant at s=" + s);
        }
    }

    @Test
    void stepMetersMatchesLegacyFormula() {
        float[] pixelSizesM = {30f, 45f, 90f, 15f};
        for (float pixelSizeM : pixelSizesM) {
            int legacy = (int)(8f * 90f / pixelSizeM);
            int current = (int)(WonderGenerator.STEP_METERS / pixelSizeM);
            assertEquals(legacy, current, "STEP_METERS must equal the legacy 8*90m scan spacing");
        }
    }
}
