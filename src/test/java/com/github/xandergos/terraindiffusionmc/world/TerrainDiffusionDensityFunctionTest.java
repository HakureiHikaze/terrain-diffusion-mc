package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Interval;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainDiffusionDensityFunctionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rangeCoversScale1To15InExtremeHeightWorld() {
        // density = targetHeight - y with targetHeight in [seaLevel-97, seaLevel+5000]
        // and y in [-2032, 2031] (the extreme-height datapack):
        //   min = (seaLevel - 97) - 2031 = seaLevel - 2128
        //   max = (seaLevel + 5000) - (-2032) = seaLevel + 7032
        int seaLevel = TerrainDiffusionConfig.seaLevel();
        Interval range = new TerrainDiffusionDensityFunction().range();
        assertEquals((float) (seaLevel - 2128), range.min());
        assertEquals((float) (seaLevel + 7032), range.max());
    }
}
