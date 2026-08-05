# Terrain Diffusion Next

[![Minecraft](https://img.shields.io/badge/Minecraft-26.x-brightgreen)](https://github.com/f1owkang/Terrain-Diffusion-Next/releases)
[![Java](https://img.shields.io/badge/Java-25-orange)]()
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE.txt)
[![Contributors](https://img.shields.io/github/contributors/f1owkang/Terrain-Diffusion-Next)](https://github.com/f1owkang/Terrain-Diffusion-Next/graphs/contributors)

**Terrain Diffusion Next** is an independent fork of [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) (targeting Minecraft 26.x) that integrates the [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) (SIGGRAPH '26) diffusion-model terrain generator into Minecraft, and extends it with rivers, beaches, aquifers, ore veins and vanilla structure support.

> 中文版： [README.md](README.md)

## Screenshots

<!-- TODO: Add in-game screenshots. 2~3 suggested: terrain panorama, river system, caves/ore veins.

     Put the images under docs/screenshots/ and reference them here, e.g.:

     <img src="docs/screenshots/terrain.jpg" width="800" alt="Terrain panorama" />
     <img src="docs/screenshots/rivers.jpg" width="800" alt="River system" />
     <img src="docs/screenshots/caves.jpg" width="800" alt="Caves and ore veins" />
-->

## What's new compared to the upstream fork

| Feature | Description |
|---------|-------------|
| **River system** | Hybrid mode (default): D8 flow-accumulation paths follow the real terrain (computed on a halo-extended window so they stay seamless across tiles) and are carved below sea level so channels actually hold water; switchable back to pure noise carving (`rivers.mode=carver`) |
| **Beach biomes** | Coastline detection (land pixels adjacent to ocean near sea level), mapped to `BEACH` / `SNOWY_BEACH` / `STONY_SHORE` |
| **Aquifers** | Re-enabled `aquifers_enabled` and restored the four vanilla noise-router channels (barrier / fluid level / lava) — cave water bodies and lava layers are back |
| **Ore veins** | Re-enabled `ore_veins_enabled` and restored the three vein channels — large iron/copper vein deposits generate |
| **Structure support** | Biome tags added for the 3 custom biomes (forest_sparse / taiga_sparse / snowy_taiga_sparse); villages, mineshafts, strongholds etc. now generate on their chunks |
| **Localization** | New Simplified Chinese language file; the "Terrain Diffusion Next" world type and settings screens display in Chinese |

## Which version should I use?

Three builds are available on the [Releases](https://github.com/f1owkang/Terrain-Diffusion-Next/releases) page:

**The CPU build is slow unless you are on MacOS.**

| Build                     | Supports                    | Setup required                          |
|---------------------------| --------------------------- | --------------------------------------- |
| **Windows** (recommended) | Windows with any modern GPU | None                                    |
| **CUDA**                  | NVIDIA GPUs                 | [CUDA + cuDNN install](CUDA_INSTALL.md) |
| **CPU**                   | Everything else             | None                                    |

> **Mac users:** the CPU build automatically uses CoreML for hardware acceleration on Apple Silicon. No extra setup is needed.

## Supported Minecraft versions

This mod targets Minecraft **26.x** (**26.1**, **26.2**, **26.3**) and requires **Java 25**. A single jar declares compatibility with the whole `>=26.1` range.

> For Minecraft 1.20.1 / 1.21.1 / 1.21.11 (the last obfuscated releases) use the upstream 2.x builds.

## Release status

> **v3.0.0 (current) is an early development release**: the Minecraft 26.x port with rivers, beaches, structures and other features still under active iteration — bugs and rough edges are expected. For a stable experience, use the upstream 2.x builds (Minecraft 1.20.1 / 1.21.1 / 1.21.11).

## Requirements

- Minecraft with [Fabric](https://fabricmc.net/) and the [Fabric API Mod](https://modrinth.com/mod/fabric-api) installed
- Windows with a GPU OR Linux with an NVIDIA GPU is strongly recommended. CPU inference works but is very slow.
- VRAM (GPU RAM) needed: 1.5GB
- RAM needed: 2.5GB (May need to increase Minecraft's RAM allocation)

## Usage

**If using CUDA build:** First see [CUDA_INSTALL.md](CUDA_INSTALL.md).

1. Download the mod jar from [Releases](https://github.com/f1owkang/Terrain-Diffusion-Next/releases) for your Minecraft version and place it in your Minecraft `mods/` folder. Make sure the Minecraft version matches.
2. Launch Minecraft, at least once online to download the models (~2.5GB).
3. Create a world, and select the **Terrain Diffusion Next** world type. Click **Customize** to set the `World Scale` (see [Per-world settings](#per-world-settings) below).
4. The mod will search for a land spawn point near the world origin automatically. If the area around (0, 0) is entirely ocean, it may take a moment to find land. Use `/td-explore` (see below) to scout the world further.

## Exploring the World

The mod includes a built-in terrain explorer web UI. Run the `/td-explore` command in-game; it will print a clickable link (e.g. `http://localhost:19801`) that opens an interactive map in your browser. Click the map on the left to open a "detailed view". Click the detailed view to get coordinates in the bottom left. You can also filter for certain climates.

Use the explorer to scout continents, mountains, rivers, islands, and other interesting terrain before venturing out in Minecraft.

## Configuration

Edit `config/terrain-diffusion-mc.properties` (created automatically on first launch, with bilingual Chinese/English comments on every option):

```
inference.device=gpu           # Inference device: cpu / gpu / auto (try GPU first, fall back to CPU)
inference.offload_models=true  # Offload inactive models from VRAM so only one model occupies the GPU at a time
validate_model=true            # Validate SHA-256 of already-downloaded model files
explorer.port=19801            # Port for the local terrain explorer web UI (started with /td-explore)
tile_size=256                  # Terrain generation region side length in blocks. Must be a power of 2.
spawn_search.initial_size=16   # Spawn search: coarse-pixel region sizes for land detection near (0,0)
spawn_search.max_size=128
```

### Per-world settings

For Terrain Diffusion Next worlds, click **Customize** in world creation and set `World Scale` (integer `1..6`). This value is saved with the world save and affects:

- how many real-world meters each block represents (`scale=1` => `30m/block`, `scale=2` => `15m/block`, etc.)
- world max height for newly created worlds (assumes tallest point is 10000 real-world meters)
- 2 is recommended for a good balance of scale and playability. Use 1 for smaller, more compressed worlds.
- Lower values put more stress on the GPU (Terrain Diffusion runs more often), while higher values put more stress on the CPU (larger world height). Most modern GPUs will be bottlenecked by the CPU around scale 2 or 3.

## Common Issues

**A dynamic link library (DLL) initialization routine failed**

This can happen for some older Java versions. Minecraft 26.x requires Java 25 or higher. The [latest Microsoft OpenJDK 25](https://learn.microsoft.com/en-us/java/openjdk/download) version is known to work.

**LoadLibrary failed with error 126** *(CUDA build only)*

This is typically due to an improper CUDA or cuDNN installation. See [CUDA_INSTALL.md](CUDA_INSTALL.md) for troubleshooting steps.

**java.lang.IllegalStateException: Failed to load terrain-diffusion models**

This typically indicates an "out of memory" error (the logs should show this as well). Terrain Diffusion's models take up about 2.5GB of RAM, so make sure to allocate enough RAM to account for this.

**If your issue is still not resolved, please [raise it here](https://github.com/f1owkang/Terrain-Diffusion-Next/issues/new).**

## Building from Source

See [BUILDING.md](BUILDING.md) for build instructions, building onnxruntime with DirectML, and notes for mod developers.

## Contributors

This project is a fork of [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc). The Terrain Diffusion ecosystem is built by the following contributors (in no particular order):

<a href="https://github.com/xandergos"><img src="https://github.com/xandergos.png" width="50" height="50" alt="xandergos" title="xandergos" /></a>
<a href="https://github.com/AirRunner"><img src="https://github.com/AirRunner.png" width="50" height="50" alt="AirRunner" title="AirRunner" /></a>
<a href="https://github.com/ThatDamnWittyWhizHard"><img src="https://github.com/ThatDamnWittyWhizHard.png" width="50" height="50" alt="ThatDamnWittyWhizHard" title="ThatDamnWittyWhizHard" /></a>
<a href="https://github.com/ayushsucksaf"><img src="https://github.com/ayushsucksaf.png" width="50" height="50" alt="ayushsucksaf" title="ayushsucksaf" /></a>
<a href="https://github.com/BillGoldenWater"><img src="https://github.com/BillGoldenWater.png" width="50" height="50" alt="BillGoldenWater" title="BillGoldenWater" /></a>
<a href="https://github.com/tlhr"><img src="https://github.com/tlhr.png" width="50" height="50" alt="tlhr" title="tlhr" /></a>
<a href="https://github.com/deforcy"><img src="https://github.com/deforcy.png" width="50" height="50" alt="deforcy" title="deforcy" /></a>
<a href="https://github.com/f1owkang"><img src="https://github.com/f1owkang.png" width="50" height="50" alt="f1owkang" title="f1owkang" /></a>

### Upstream projects

- [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) — the diffusion-model terrain generator (SIGGRAPH '26 / InfiniteDiffusion)
- [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) — the upstream mod this project forks
- [infinite-tensor](https://github.com/xandergos/infinite-tensor) — the streaming tile tensor library
