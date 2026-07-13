package com.github.xandergos.terraindiffusionmc.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Concrete terrain-diffusion density function for Minecraft 26.3+, which replaced
 * {@code minValue()} / {@code maxValue()} with a single {@code range()} returning
 * {@code net.minecraft.util.Interval}.
 *
 * <p>The shared behaviour lives in {@link AbstractTerrainDiffusionDensityFunction}; this class only
 * carries the codec plumbing and the version-specific range declaration. The build selects this
 * source directory ({@code src/mc_263}) for the 26.3 target.
 */
public class TerrainDiffusionDensityFunction extends AbstractTerrainDiffusionDensityFunction {
    public static final MapCodec<TerrainDiffusionDensityFunction> CODEC =
            MapCodec.unit(TerrainDiffusionDensityFunction::new);

    public static final KeyDispatchDataCodec<TerrainDiffusionDensityFunction> CODEC_HOLDER = KeyDispatchDataCodec.of(CODEC);

    @Override
    public Interval range() {
        return Interval.of(MIN_DENSITY, MAX_DENSITY);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC_HOLDER;
    }
}
