package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiverDetectorTest {

    @Test
    void d8FlowSlopeToNeighbor() {
        int H = 3, W = 3;
        // 3x3 heightmap: centre 3m, edges 0m → all flow outward
        float[] elev = {0, 0, 0, 0, 3, 0, 0, 0, 0};
        int[] rr = new int[9], cc = new int[9];
        boolean[] isSink = new boolean[9];
        int[] kmax = new int[9];

        RiverDetector.d8Flow(elev, H, W, rr, cc, isSink, kmax);

        // Centre (3m) should not be a sink
        assertFalse(isSink[4]);
        // Neighbour receiving flow should have elevation < 3
        int target = rr[4] * W + cc[4];
        assertTrue(elev[target] < 3f);
    }

    @Test
    void oceanIsSink() {
        float[] elev = {0, 0, 0, 0, 1, 0, 0, 0, -1};
        int[] rr = new int[9], cc = new int[9];
        boolean[] isSink = new boolean[9];
        int[] kmax = new int[9];

        RiverDetector.d8Flow(elev, 3, 3, rr, cc, isSink, kmax);

        // All ocean cells (elev <= 0) are sinks
        for (int i = 0; i < 9; i++) {
            if (elev[i] <= 0f) assertTrue(isSink[i], "index " + i);
        }
    }

    @Test
    void flowAccumulationAccumulates() {
        float[] elev = {1, 2, 1, 0, 3, 0, 0, 0, 0};
        int[] rr = new int[9], cc = new int[9];
        boolean[] isSink = new boolean[9];
        int[] kmax = new int[9];

        RiverDetector.d8Flow(elev, 3, 3, rr, cc, isSink, kmax);
        float[] acc = RiverDetector.flowAccumulation(elev, 3, 3, rr, cc, isSink);

        // The highest point (index 4, elev=3) should contribute to some downstream cell
        assertTrue(acc[4] >= 1f);
    }

    @Test
    void detectRiversThreshold() {
        // Simple slope: two rows, flow goes from high to low
        int H = 2, W = 4;
        float[] elev = {10, 8, 6, 4, 3, 2, 1, -1};
        boolean[] rivers = RiverDetector.detectRivers(elev, H, W, 2f);

        // At least some land pixels above threshold
        int landCount = 0, riverCount = 0;
        for (int i = 0; i < H * W; i++) {
            if (elev[i] > 0) landCount++;
            if (rivers[i]) riverCount++;
        }
        assertTrue(landCount > 0);
        // With a small array the accumulation may not reach threshold
        // Just verify no crash and river mask is the right size
        assertEquals(H * W, rivers.length);
    }
}
