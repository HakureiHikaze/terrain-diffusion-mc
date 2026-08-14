package com.github.xandergos.terraindiffusionmc.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeightConverterTest {

    private static final float NATIVE_RESOLUTION = 30f;

    @Test
    void negativeElevationIsUnscaledAcrossScales() {
        short[] depths = {-1, -10, -100, -1000, -3000};
        for (short depth : depths) {
            int reference = HeightConverter.convertToMinecraftHeight(depth, 1, NATIVE_RESOLUTION);
            for (int scale = 2; scale <= 6; scale++) {
                assertEquals(reference,
                        HeightConverter.convertToMinecraftHeight(depth, scale, NATIVE_RESOLUTION),
                        "underwater depth must not scale at " + depth + "m");
            }
        }
    }

    @Test
    void positiveElevationScalesLinearlyWithWorldScale() {
        short meters = 1000;
        int scale1 = HeightConverter.convertToMinecraftHeight(meters, 1, NATIVE_RESOLUTION);
        int scale2 = HeightConverter.convertToMinecraftHeight(meters, 2, NATIVE_RESOLUTION);
        assertEquals(scale1 + (int)(meters / NATIVE_RESOLUTION), scale2,
                "land elevation must scale linearly with world scale");
    }
}
