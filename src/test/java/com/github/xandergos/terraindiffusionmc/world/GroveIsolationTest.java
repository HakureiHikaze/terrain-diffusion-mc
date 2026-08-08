package com.github.xandergos.terraindiffusionmc.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroveIsolationTest {

    @Test
    void customGroveExistsAndIsTaggedAsOverworld() {
        // Whether the mod jar still overrides data/minecraft/worldgen/biome/grove.json
        // is verified on the built artifact (jar tf), because the vanilla game jar
        // is also on the test classpath and would satisfy getResourceAsStream.
        assertNotNull(resource("/data/terrain-diffusion-mc/worldgen/biome/grove.json"),
                "custom terrain-diffusion-mc:grove biome missing");

        JsonArray isOverworld = readJsonArray("/data/minecraft/tags/worldgen/biome/is_overworld.json");
        assertTrue(containsId(isOverworld, "terrain-diffusion-mc:grove"),
                "custom grove missing from is_overworld tag");

        JsonArray isForest = readJsonArray("/data/minecraft/tags/worldgen/biome/is_forest.json");
        assertTrue(containsId(isForest, "terrain-diffusion-mc:grove"),
                "custom grove missing from is_forest tag");
    }

    private static InputStream resource(String path) {
        return GroveIsolationTest.class.getResourceAsStream(path);
    }

    private static JsonArray readJsonArray(String path) {
        try (InputStream in = resource(path)) {
            assertNotNull(in, "missing tag resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("values");
        } catch (Exception e) {
            throw new AssertionError("Failed to read " + path, e);
        }
    }

    private static boolean containsId(JsonArray values, String id) {
        for (JsonElement value : values) {
            if (id.equals(value.getAsString())) {
                return true;
            }
        }
        return false;
    }
}
