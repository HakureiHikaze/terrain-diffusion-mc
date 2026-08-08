package com.github.xandergos.terraindiffusionmc.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialRuleStructureTest {

    @Test
    void surfaceSequenceIsScopedAbovePreliminarySurfaceAndGrassAvoidsShallowSeabeds() {
        JsonObject root = readMaterialRule();
        JsonArray sequence = root.getAsJsonArray("sequence");

        // Deepslate anchor stays relative to sea level (top of the rule, before the surface wrapper).
        JsonObject deepslateCondition = findConditionByType(sequence, "minecraft:vertical_gradient");
        assertNotNull(deepslateCondition, "deepslate vertical_gradient condition missing");
        JsonObject ifTrue = deepslateCondition.getAsJsonObject("if_true");
        assertEquals(4, ifTrue.getAsJsonObject("false_at_and_above").get("relative_to_sea_level").getAsInt());
        assertEquals(-4, ifTrue.getAsJsonObject("true_at_and_below").get("relative_to_sea_level").getAsInt());

        // Surface sequence wrapped in above_preliminary_surface.
        JsonObject surfaceWrapper = findConditionByType(sequence, "minecraft:above_preliminary_surface");
        assertNotNull(surfaceWrapper, "above_preliminary_surface wrapper missing");
        JsonObject surfaceSequence = surfaceWrapper.getAsJsonObject("then_run");
        assertEquals("minecraft:sequence", surfaceSequence.get("type").getAsString());

        // grass_block must be reachable only under not(water): 26.3 has no
        // not_underwater condition type, so the datapack uses "not" + "water".
        JsonElement grassBlock = findBlockRule(surfaceSequence, "minecraft:grass_block");
        assertNotNull(grassBlock, "grass_block rule missing");
        JsonObject parentCondition = findEnclosingCondition(root, grassBlock, "minecraft:not");
        assertNotNull(parentCondition, "grass_block rule is not guarded by not(water)");
        JsonObject invert = parentCondition.getAsJsonObject("if_true").getAsJsonObject("invert");
        assertEquals("minecraft:water", invert.get("type").getAsString());
    }

    private static JsonObject readMaterialRule() {
        try (InputStream in = MaterialRuleStructureTest.class.getResourceAsStream(
                "/data/terrain-diffusion-mc/worldgen/material_rule/terrain_diffusion.json")) {
            assertNotNull(in, "material_rule resource missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("Failed to read material_rule", e);
        }
    }

    private static JsonObject findConditionByType(JsonArray sequence, String ifType) {
        for (JsonElement element : sequence) {
            if (!element.isJsonObject()) {
                continue; // e.g. "minecraft:bedrock_floor" string reference
            }
            JsonObject condition = element.getAsJsonObject();
            if ("minecraft:condition".equals(condition.get("type").getAsString())
                    && condition.has("if_true")
                    && ifType.equals(condition.getAsJsonObject("if_true").get("type").getAsString())) {
                return condition;
            }
        }
        return null;
    }

    private static JsonElement findBlockRule(JsonElement node, String resultState) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            String type = object.has("type") ? object.get("type").getAsString() : null;
            if ("minecraft:block".equals(type)
                    && resultState.equals(object.get("result_state").getAsString())) {
                return object;
            }
            for (String key : object.keySet()) {
                JsonElement found = findBlockRule(object.get(key), resultState);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray()) {
                JsonElement found = findBlockRule(element, resultState);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JsonObject findEnclosingCondition(JsonElement node, JsonElement target, String conditionType) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (object.has("type")
                    && "minecraft:condition".equals(object.get("type").getAsString())
                    && object.has("if_true")
                    && object.get("if_true").isJsonObject()
                    && conditionType.equals(object.getAsJsonObject("if_true").get("type").getAsString())) {
                JsonObject thenRun = object.getAsJsonObject("then_run");
                if (contains(thenRun, target)) {
                    return object;
                }
            }
            for (String key : object.keySet()) {
                JsonObject found = findEnclosingCondition(object.get(key), target, conditionType);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray()) {
                JsonObject found = findEnclosingCondition(element, target, conditionType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean contains(JsonElement node, JsonElement target) {
        if (node.equals(target)) {
            return true;
        }
        if (node.isJsonObject()) {
            for (String key : node.getAsJsonObject().keySet()) {
                if (contains(node.getAsJsonObject().get(key), target)) {
                    return true;
                }
            }
        } else if (node.isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray()) {
                if (contains(element, target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
