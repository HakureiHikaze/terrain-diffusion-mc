# AGENTS.md

Fabric mod (Minecraft 26.x, Java 25) integrating the Terrain Diffusion diffusion-model terrain generator (Java port of `terrain_diffusion/inference/world_pipeline.py`) via ONNX Runtime. Fork of [xandergos/terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc).

**Branch note:** work happens on `mc26` (the mc 26.x port, cherry-picked from upstream PR #223, commit `202f9d5`). `master` is the old 2.2.0 / Minecraft 1.21.11 build (Yarn mappings, Java 21) — keep it untouched. Minecraft 26.x is unobfuscated, so all code uses Mojang official class names (`Component`, `LevelStem`, `Holder`, ...), not Yarn.

## Build

Two orthogonal selectors: inference variant (`useDml`/`useCuda`/`useCpu`, mutually exclusive, **DML default**) and MC target (`mcTarget`, default **261**):

```
./gradlew build -PuseDml=true -PmcTarget=261   # Windows (DirectML); needs libs/onnxruntime-dml.jar
./gradlew build -PuseCuda=true -PmcTarget=262  # NVIDIA
./gradlew build -PuseCpu=true -PmcTarget=263   # CPU + CoreML on macOS
./gradlew buildAllMc                           # all three MC targets (nested gradlew, slow)
```

- Valid `mcTarget` values: 261 (26.1.2), 262 (26.2), 263 (26.3-snapshot-3), defined in `gradle.properties` as `mc261_*`/`mc262_*`/`mc263_*`; unknown values throw a GradleException.
- Requires **JDK 25** (local: `C:\Program Files\Java\jdk-25.0.4`, set as user `JAVA_HOME`) and Gradle 9.5.1 wrapper. No yarn mappings — Minecraft 26.1+ is unobfuscated.
- Version becomes `{mod_version}-{windows|cuda|cpu}+{minecraft_version}` (currently `3.0.0-...`).
- `processResources` depends on `generateModelAssetManifest`, which **queries the Hugging Face API at build time** (pinned commit `ad2df557eca5645f588766101cf3bc3682455c3e` of `xandergos/terrain-diffusion-30m-onnx`). Building offline fails.
- `./gradlew pipelineTest` runs `PipelineTest.main` (JavaExec, `-Xmx8g`): downloads real models, generates one 256-block tile, fails if VRAM delta exceeds 2500 MB (measured via `nvidia-smi`). Not a JUnit test — there is no test framework in this repo.
- `./gradlew runClient` for the dev client; sources jar via `-PwithSourcesJar=true`. All JavaExec tasks force `log4j2-dev.xml` from the repo root.

## Network (dev machine)

- Outbound traffic goes through a local proxy `http://127.0.0.1:7897`. The Gradle **wrapper** downloader reads JVM proxy system properties, not env vars, so use:
  ```
  $env:JAVA_OPTS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897"
  ```
- First build also downloads Gradle 9.5.1 (~150 MB) — raise `-Dorg.gradle.wrapper.networkTimeout=60000` if the 10 s default times out.
- HF model downloads at runtime need the same proxy on the JVM running the game.

## Runtime model assets

- ONNX models are **not in the repo**. On first launch they download (~2.5 GB) into `<game dir>/terrain-diffusion-models` and are SHA-256-validated against the manifest generated at build time (disable via `validate_model=false` in `config/terrain-diffusion-mc.properties`).
- `libs/onnxruntime-dml.jar` is git-force-added even though `libs/` is gitignored — never delete it, and don't expect other files placed in `libs/` to show up in git.
- `inference.device` is forced to `auto` on the CPU build (CoreML on macOS, CPU elsewhere).

## Architecture

- `pipeline/` — the heavy lifting: `WorldPipeline` (3 stages: coarse 20-step DPM-Solver++, latent 2-step flow matching, decoder 1-step; tile-based with stride), `BiomeClassifier` (hand-written elevation + 4 climate vars → biome rules; README calls this the intended place to improve biome quality), `ModelAssetManager`, `PipelineModels` (shared model lifecycle), `SyntheticMapFactory` + `PortableRng`/`FastNoiseLite` (seeded synthetic prior). This code is MC-version-independent and was untouched by the 26.x port.
- `infinitetensor/` — custom streaming tile tensor library (`InfiniteTensor`, `MemoryTileStore` ~100 MB cache) decoupled from Minecraft; pipeline math lives here and in `pipeline/`.
- `world/` — Minecraft integration: `TerrainDiffusionBiomeSource` and the density function (registered as codecs under `terrain_diffusion` in `TerrainDiffusionMc.onInitialize`); density function pulls tile heightmaps via `LocalTerrainProvider` (keyed by world seed); `WorldScaleManager` handles per-world scale 1..6; `HeightConverter` maps meters → Minecraft Y.
- **DensityFunction value-range API diverges across 26.x**: `AbstractTerrainDiffusionDensityFunction` holds the shared logic (src/main); the version-specific subclass lives in `src/mc_263/java` (26.3, `range()` → `net.minecraft.util.Interval`) or `src/mc_legacy/java` (26.1/26.2, `minValue()`/`maxValue()`) — selected by `build.gradle:46` via `mcTarget`. Don't put shared logic in the variant directories.
- `explorer/` — embedded HTTP server for the `/td-explore` web UI (port 19801; page at `src/main/resources/assets/.../explorer/index.html`).
- Client "Customize" button works via `mixin/client/WorldCreationUiStateMixin` (26.x mechanism: inject a `PresetEditor` into `WorldCreationUiState#getPresetEditor` for the Terrain Diffusion world preset); `WorldScaleSettingsScreen` edits the overworld `dimension_type` and persists the scale via `WorldScaleSelectionState`/`WorldScaleSettingsState` (SavedData).
- Datagen and client entrypoints exist but are empty; worldgen data lives in `src/main/resources/data/` (world preset, dimension types `terrain_diffusion_scale_1..6`, noise settings, biome/placed-feature overrides). 26.x `dimension_type` JSONs need `default_clock` + `has_ender_dragon_fight` fields, which the existing files now carry.
- Worldgen data `data/minecraft/worldgen/biome/grove.json` overrides a vanilla biome — changes affect vanilla worlds too, not just Terrain Diffusion presets.
- `fabric.mod.json` declares `depends` on `"fabric-api"` (not `"fabric"`) and `"minecraft": ">=26.1"` (via `minecraft_dependency` property).

## Upstream references

- [xandergos/terrain-diffusion](https://github.com/xandergos/terrain-diffusion) — the original Python project (SIGGRAPH '26 / InfiniteDiffusion). `pipeline/` and `infinitetensor/` here are Java ports of `terrain_diffusion/inference/world_pipeline.py` and the infinite-tensor framework; when porting math, read the Python source first. `terrain_diffusion/inference/synthetic_map.py` is the upstream of `SyntheticMapFactory` and the intended place to change base-world generation.
- [xandergos/infinite-tensor](https://github.com/xandergos/infinite-tensor) — the Python infinite-tensor library that `infinitetensor/` ports (concepts: `TensorWindow`, `InfiniteTensor`, `TileStore`).
- Upstream PR [xandergos/terrain-diffusion-mc#223](https://github.com/xandergos/terrain-diffusion-mc/pull/223) — the mc 26.x port this branch cherry-picked from (commit `202f9d5`, locally `8fd0297`); useful reference when fixing 26.x mapping issues.
