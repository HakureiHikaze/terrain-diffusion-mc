package com.github.xandergos.terraindiffusionmc.mixin;

import net.minecraft.SharedConstants;
import net.minecraft.world.level.DimensionType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Keeps the lava/water boundary relative to the world bottom.
 *
 * <p>26.3's {@code NoiseBasedChunkGenerator.createFluidPicker} fills lava below
 * {@code Math.min(-54, seaLevel)}. In extreme-height worlds (min_y=-2032) the
 * hardcoded -54 makes the entire ocean floor lava. Vanilla's -54 is the world
 * bottom + 10, so we express it via {@code noiseSettings.minY() + 10} instead,
 * which is identical at default settings (-64 + 10 = -54).
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorFluidMixin {

    /**
     * @author terrain-diffusion-mc
     * @reason lava threshold must follow the world bottom in extreme-height worlds
     */
    @Overwrite
    private static Aquifer.FluidPicker createFluidPicker(final NoiseGeneratorSettings settings) {
        int lavaLevel = settings.noiseSettings().minY() + 10;
        Aquifer.FluidStatus lavaStatus = new Aquifer.FluidStatus(lavaLevel, Blocks.LAVA.defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus seaStatus = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        Aquifer.FluidStatus emptyStatus = new Aquifer.FluidStatus(DimensionType.MIN_Y * 2, Blocks.AIR.defaultBlockState());
        return (x, y, z) -> {
            if (SharedConstants.DEBUG_DISABLE_FLUID_GENERATION) {
                return emptyStatus;
            } else {
                return y < Math.min(lavaLevel, seaLevel) ? lavaStatus : seaStatus;
            }
        };
    }
}
