package com.github.xandergos.terraindiffusionmc.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Concrete terrain-diffusion density function for Minecraft 26.1 and 26.2, which declare the
 * value range via {@code minValue()} / {@code maxValue()}.
 *
 * <p>The shared behaviour lives in {@link AbstractTerrainDiffusionDensityFunction}; this class only
 * carries the codec plumbing and the version-specific range declaration. The build selects this
 * source directory ({@code src/mc_legacy}) for the 26.1 and 26.2 targets.
 */
public class TerrainDiffusionDensityFunction extends AbstractTerrainDiffusionDensityFunction {
    public static final MapCodec<TerrainDiffusionDensityFunction> CODEC =
            MapCodec.unit(TerrainDiffusionDensityFunction::new);

    public static final KeyDispatchDataCodec<TerrainDiffusionDensityFunction> CODEC_HOLDER = KeyDispatchDataCodec.of(CODEC);

    @Override
    public double minValue() {
        return MIN_DENSITY;
    }

    @Override
    public double maxValue() {
        return MAX_DENSITY;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC_HOLDER;
    }
}
