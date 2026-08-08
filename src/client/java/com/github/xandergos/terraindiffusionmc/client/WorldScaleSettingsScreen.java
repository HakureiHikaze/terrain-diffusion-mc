package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.world.WorldScaleSelectionState;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * World creation settings screen for selecting the initial terrain scale of a world.
 *
 * <p>The scale only affects terrain resolution (meters per block). Dimension
 * height limits are not managed by the mod; they are defined by datapacks.
 */
public final class WorldScaleSettingsScreen extends Screen {
    private static final int TEXT_FIELD_WIDTH = 80;
    private static final int TEXT_FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

    private static final Component LABEL_TEXT = Component.literal("World Scale");

    private final Screen parentScreen;
    private final int maxScale;
    private final Component descriptionText;
    private final Component errorText;
    private EditBox scaleTextField;
    private StringWidget validationTextWidget;

    public WorldScaleSettingsScreen(Screen parentScreen, int maxScale) {
        super(Component.translatable("terrain-diffusion-mc.world_settings.title"));
        this.parentScreen = parentScreen;
        this.maxScale = Math.max(1, maxScale);
        this.descriptionText = Component.literal("Enter an integer value (1-" + this.maxScale + ")");
        this.errorText = Component.literal("Scale must be an integer between 1 and " + this.maxScale);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addCenteredTextWidget(this.title, centerX, 20, 0xFFFFFF);

        addCenteredTextWidget(descriptionText, centerX, centerY - 34, 0xAAAAAA);
        addCenteredTextWidget(LABEL_TEXT, centerX, centerY - 22, 0xFFFFFF);

        scaleTextField = new EditBox(this.font,
                centerX - TEXT_FIELD_WIDTH / 2, centerY - 10,
                TEXT_FIELD_WIDTH, TEXT_FIELD_HEIGHT,
                LABEL_TEXT);
        scaleTextField.setValue(String.valueOf(WorldScaleSelectionState.getPendingScaleOrDefault()));
        scaleTextField.setResponder(value -> validationTextWidget.setMessage(Component.empty()));
        this.addRenderableWidget(scaleTextField);
        this.setInitialFocus(scaleTextField);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onDonePressed())
                .bounds(centerX - BUTTON_WIDTH - 5, centerY + 20, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(centerX + 5, centerY + 20, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        validationTextWidget = new StringWidget(0, centerY + 46, this.width, 9, Component.empty(), this.font);
        this.addRenderableWidget(validationTextWidget);
    }

    /**
     * Adds a centered StringWidget at the given screen-center x and y position.
     */
    private void addCenteredTextWidget(Component text, int centerX, int y, int color) {
        int textWidth = this.font.width(text);
        Component coloredText = text.copy().withColor(color);
        StringWidget widget = new StringWidget(centerX - textWidth / 2, y, textWidth, 9, coloredText, this.font);
        this.addRenderableWidget(widget);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(parentScreen);
        }
    }

    /**
     * Parses and validates the chosen scale, then stores it as a pending world-creation value.
     */
    private void onDonePressed() {
        String rawScaleValue = scaleTextField.getValue().trim();
        if (rawScaleValue.isEmpty()) {
            validationTextWidget.setMessage(errorText);
            return;
        }
        try {
            int selectedScale = Integer.parseInt(rawScaleValue);
            if (selectedScale < 1 || selectedScale > maxScale) {
                validationTextWidget.setMessage(errorText);
                return;
            }
            WorldScaleSelectionState.setPendingScale(selectedScale);
            onClose();
        } catch (NumberFormatException exception) {
            validationTextWidget.setMessage(errorText);
        }
    }
}
