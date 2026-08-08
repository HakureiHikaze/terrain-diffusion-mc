package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeClassifier;
import com.github.xandergos.terraindiffusionmc.pipeline.CoarseBiomeClassifier;
import com.github.xandergos.terraindiffusionmc.world.CoarseBiomeSearch.CoarseMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoarseMapTest {

    @Test
    void classifyPixelMatchesPerPixelClassification() {
        int width = 3;
        int height = 2;
        int perUnit = 64;
        int plane = width * height;
        FloatTensor tensor = new FloatTensor(new int[]{7, height, width});

        // Pixel (i=1, j=1): weight 1, elevSqrt 3, temp 10, tempStd 1, precip 500, precipStd 2.
        int p11 = 1 * width + 1;
        tensor.data[p11] = 3.0f;                 // channel 0: elevSqrt
        tensor.data[2 * plane + p11] = 10.0f;    // channel 2: temp
        tensor.data[3 * plane + p11] = 1.0f;     // channel 3: tempStd
        tensor.data[4 * plane + p11] = 500.0f;   // channel 4: precip
        tensor.data[5 * plane + p11] = 2.0f;     // channel 5: precipStd
        tensor.data[6 * plane + p11] = 1.0f;     // channel 6: weight

        // Pixel (i=0, j=2): non-unit weight 2, elevSqrt 6 -> normalized 3, temp 20.
        int p02 = 0 * width + 2;
        tensor.data[p02] = 6.0f;
        tensor.data[2 * plane + p02] = 40.0f;
        tensor.data[3 * plane + p02] = 0.5f;
        tensor.data[4 * plane + p02] = 300.0f;
        tensor.data[5 * plane + p02] = 1.0f;
        tensor.data[6 * plane + p02] = 2.0f;

        CoarseMap map = new CoarseMap(-2, 1, width, height, perUnit, tensor.data);

        short expected11 = CoarseBiomeClassifier.classify(3.0f, 10.0f, 1.0f, 500.0f, 2.0f);
        short expected02 = CoarseBiomeClassifier.classify(3.0f, 20.0f, 0.25f, 150.0f, 0.5f);

        // Blocks inside pixel (ci=2, cj=-1) map back to it, including negatives.
        assertEquals(expected11, map.classifyPixel(-1 * perUnit + 10, 2 * perUnit + 7));
        assertEquals(expected11, map.classifyPixel(-1 * perUnit, 2 * perUnit));
        // Blocks inside pixel (ci=1, cj=0) map back to it.
        assertEquals(expected02, map.classifyPixel(0 * perUnit + 63, 1 * perUnit + 63));
    }

    @Test
    void zeroWeightAndOutOfRangeReturnPlains() {
        int width = 1;
        int height = 1;
        FloatTensor tensor = new FloatTensor(new int[]{7, height, width});
        CoarseMap map = new CoarseMap(0, 0, width, height, 64, tensor.data);

        assertEquals(BiomeClassifier.PLAINS, map.classifyPixel(0, 0));
        assertEquals(BiomeClassifier.PLAINS, map.classifyPixel(-1000, 5000));
        assertEquals(BiomeClassifier.PLAINS, map.classifyPixel(100, 100));
    }
}
