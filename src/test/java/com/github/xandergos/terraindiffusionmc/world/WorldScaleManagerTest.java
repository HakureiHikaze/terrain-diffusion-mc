package com.github.xandergos.terraindiffusionmc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldScaleManagerTest {

    @Test
    void maxScaleForBundledDimension() {
        // Bundled terrain_diffusion dimension: min_y=-64, height=800 (top 735),
        // default sea_level=63 -> only scales 1-2 fit.
        assertEquals(2, WorldScaleManager.maxFitScaleForDimension(-64, 800, 63));
    }

    @Test
    void maxScaleForExtremeDatapack() {
        // -2032..2031 datapack with sea_level=-1904 -> scales 1-11 fit.
        assertEquals(11, WorldScaleManager.maxFitScaleForDimension(-2032, 4064, -1904));
    }

    @Test
    void maxScaleForVanillaWorld() {
        // Vanilla overworld (min_y=-64, height=384): even scale 1 needs 431 blocks.
        assertEquals(1, WorldScaleManager.maxFitScaleForDimension(-64, 384, 63));
    }

    @Test
    void maxScaleIsBounded() {
        // Deepest allowed sea_level on the largest dimension cannot exceed 12,
        // and undersized dimensions clamp up to 1.
        assertEquals(12, WorldScaleManager.maxFitScaleForDimension(-2032, 4064, -2032));
        assertEquals(1, WorldScaleManager.maxFitScaleForDimension(0, 16, 63));
        for (int sea : new int[]{-2032, -1904, 0, 63, 2031}) {
            int result = WorldScaleManager.maxFitScaleForDimension(-2032, 4064, sea);
            assertEquals(Math.max(1, Math.min(15, result)), result);
        }
    }

    @Test
    void fitLimitIsAdvisoryNotHardCap() {
        // Players may still select scale 12-15; the fit limit only drives warnings.
        assertEquals(15, WorldScaleManager.clampScale(15));
        assertEquals(2, WorldScaleManager.maxFitScaleForDimension(-64, 800, 63));
        assertEquals(12, WorldScaleManager.clampScale(12));
    }
}
