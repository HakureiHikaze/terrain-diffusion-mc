package com.github.xandergos.terraindiffusionmc.mixin.client;

import com.github.xandergos.terraindiffusionmc.client.WorldScaleSettingsScreen;
import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Reuses vanilla's World tab "Customize" button for Terrain Diffusion worlds.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class CreateWorldScreenWorldTabMixin {

    @Shadow
    @Final
    CreateWorldScreen this$0;

    private static final ResourceKey<WorldPreset> TERRAIN_DIFFUSION_PRESET_KEY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "terrain_diffusion"));

    @Inject(method = "lambda$new$5", at = @At("HEAD"), cancellable = true)
    private static void terrainDiffusionMc$forceCustomizeAvailable(CreateWorldScreen screen, CallbackInfoReturnable<Boolean> cir) {
        if (isTerrainDiffusionWorldTypeSelected(screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "lambda$new$6", at = @At("HEAD"), cancellable = true)
    private static void terrainDiffusionMc$forceCustomizeVisible(CreateWorldScreen screen, CallbackInfoReturnable<Boolean> cir) {
        if (isTerrainDiffusionWorldTypeSelected(screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "openPresetEditor", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$openTerrainScaleScreen(CallbackInfo ci) {
        if (!isTerrainDiffusionWorldTypeSelected(this$0)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreenAndShow(new WorldScaleSettingsScreen(this$0, resolveMaxScale(this$0)));
            ci.cancel();
        }
    }

    /**
     * Resolves the maximum scale supported by the selected world preset's
     * overworld dimension. Falls back to the global maximum when the preset
     * or dimension cannot be resolved.
     */
    private static int resolveMaxScale(CreateWorldScreen screen) {
        try {
            WorldCreationUiState uiState = screen.getUiState();
            if (uiState == null || uiState.getWorldType() == null) {
                return WorldScaleManager.MAX_SCALE;
            }
            WorldPreset preset = uiState.getWorldType().preset().value();
            Optional<LevelStem> overworld = preset.overworld();
            if (overworld.isEmpty()) {
                return WorldScaleManager.MAX_SCALE;
            }
            DimensionType dimensionType = overworld.get().type().value();
            return WorldScaleManager.maxScaleForDimension(
                    dimensionType.minY(), dimensionType.height(), TerrainDiffusionConfig.seaLevel());
        } catch (RuntimeException e) {
            return WorldScaleManager.MAX_SCALE;
        }
    }

    private static boolean isTerrainDiffusionWorldTypeSelected(CreateWorldScreen screen) {
        WorldCreationUiState uiState = screen.getUiState();
        if (uiState == null) {
            return false;
        }
        WorldCreationUiState.WorldTypeEntry worldType = uiState.getWorldType();
        if (worldType == null) {
            return false;
        }
        return worldType.preset().is(TERRAIN_DIFFUSION_PRESET_KEY);
    }
}
