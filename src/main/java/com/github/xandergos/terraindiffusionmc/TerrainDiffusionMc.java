package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.explorer.ExplorerServer;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.ModelAssetManager;
import com.github.xandergos.terraindiffusionmc.pipeline.PipelineModels;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionDensityFunction;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import java.net.URI;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

public class TerrainDiffusionMc implements ModInitializer {
    public static final String MOD_ID = "terrain-diffusion-mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    @Override
    public void onInitialize() {
        LOG.info("Initializing terrain-diffusion-mc");
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath(MOD_ID, "terrain_diffusion"), TerrainDiffusionBiomeSource.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "terrain_diffusion"), TerrainDiffusionDensityFunction.CODEC);

        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> LocalTerrainProvider.clearCache());

        ServerLevelEvents.LOAD.register((server, world) -> {
            if (world.dimension() == Level.OVERWORLD) {
                WorldScaleManager.initializeForWorld(world);
                LocalTerrainProvider.init(world.getSeed());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ExplorerServer.stop());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("td-explore").executes(TerrainDiffusionMc::executeExplore));
            dispatcher.register(literal("td-preload")
                    .then(argument("radius", StringArgumentType.word())
                            .executes(TerrainDiffusionMc::executePreload)));
        });
    }

    private static int executeExplore(CommandContext<CommandSourceStack> ctx) {
        try {
            int port = ExplorerServer.startIfNotRunning();
            String url = "http://localhost:" + port;
            MutableComponent link = Component.literal(url)
                    .withStyle(s -> s.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                                  .withUnderlined(true));
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Terrain Explorer: ").append(link),
                    false);
        } catch (Exception e) {
            LOG.error("Failed to start terrain explorer", e);
            ctx.getSource().sendFailure(Component.literal("Failed to start terrain explorer: " + e.getMessage()));
        }
        return 1;
    }

    /**
     * /td-preload &lt;radius&gt; — generate full chunks in a circular radius around the executor
     * (or world spawn for console) on a background thread, so terrain is ready before players
     * explore. Useful when worldgen.skip_initial_chunk_preload is enabled.
     */
    private static int executePreload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        if (level == null) {
            src.sendFailure(Component.literal("No server level available."));
            return 0;
        }
        int radius;
        try {
            radius = Math.max(1, Math.min(64, Integer.parseInt(StringArgumentType.getString(ctx, "radius"))));
        } catch (NumberFormatException e) {
            src.sendFailure(Component.literal("Radius must be a number (1-64)."));
            return 0;
        }

        BlockPos center;
        if (src.getEntity() != null) {
            center = src.getEntity().blockPosition();
        } else {
            center = level.getRespawnData().pos();
        }
        final int centerX = center.getX() >> 4;
        final int centerZ = center.getZ() >> 4;

        Thread worker = new Thread(() -> {
            int count = 0;
            int r2 = radius * radius;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dz * dz > r2) continue;
                    level.getChunk(centerX + dx, centerZ + dz);
                    count++;
                }
            }
            final int total = count;
            level.getServer().execute(() -> src.sendSuccess(
                    () -> Component.literal("Preloaded " + total + " chunks around " + center.getX() + ", " + center.getZ()),
                    false));
        }, "td-preload");
        worker.setDaemon(true);
        worker.start();

        src.sendSuccess(() -> Component.literal(
                "Preloading terrain in radius " + radius + " chunks around " + center.getX() + ", " + center.getZ() + " (background)…"), false);
        return 1;
    }
}
