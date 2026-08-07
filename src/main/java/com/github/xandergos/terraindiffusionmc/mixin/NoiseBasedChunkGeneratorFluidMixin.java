package com.github.xandergos.terraindiffusionmc.mixin;

import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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

    @Redirect(method = "createFluidPicker", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
    private static int terrainDiffusionMc$lavaThresholdRelativeToBottom(
            int lavaLevel, int seaLevel, NoiseGeneratorSettings settings) {
        return Math.min(settings.noiseSettings().minY() + 10, seaLevel);
    }
}
