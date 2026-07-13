package com.github.xandergos.terraindiffusionmc.mixin.client;

import com.github.xandergos.terraindiffusionmc.client.WorldScaleSettingsScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reuses vanilla's World tab "Customize" button for Terrain Diffusion worlds.
 *
 * <p>In Minecraft 26.1 the World tab derives the customize button's availability and its
 * action from {@link WorldCreationUiState#getPresetEditor()}: the button is active whenever a
 * {@link PresetEditor} exists for the selected world preset, and clicking it opens the screen
 * that editor produces. Terrain Diffusion has no built-in preset editor, so we supply one that
 * opens the {@link WorldScaleSettingsScreen}. This single hook both enables the button and wires
 * up the click, replacing the several tab-internal injections needed on older versions.
 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    private static final ResourceKey<WorldPreset> TERRAIN_DIFFUSION_PRESET_KEY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "terrain_diffusion"));

    @Inject(method = "getPresetEditor", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$customizeTerrainDiffusion(CallbackInfoReturnable<PresetEditor> cir) {
        WorldCreationUiState self = (WorldCreationUiState) (Object) this;
        WorldCreationUiState.WorldTypeEntry worldType = self.getWorldType();
        if (worldType == null) {
            return;
        }
        Holder<WorldPreset> preset = worldType.preset();
        if (preset == null) {
            return;
        }
        if (preset.unwrapKey().filter(TERRAIN_DIFFUSION_PRESET_KEY::equals).isPresent()) {
            cir.setReturnValue((parent, settings) -> new WorldScaleSettingsScreen(parent));
        }
    }
}
