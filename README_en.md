# Terrain Diffusion Next

<p align="center">
  <img src="site/assets/logo.svg" width="120" height="120" alt="Terrain Diffusion Next" title="Terrain Diffusion Next">
</p>

<p align="center">
  <em>The icon is AI-generated — community design submissions are welcome via <a href="https://github.com/f1owkang/Terrain-Diffusion-Next/issues/new">Issues</a>.</em>
</p>

<p align="center">
  <a href="https://github.com/f1owkang/Terrain-Diffusion-Next/releases"><img src="https://img.shields.io/badge/Minecraft-26.x-brightgreen?style=flat-square" alt="Minecraft 26.x"></a>
  <a href=""><img src="https://img.shields.io/badge/Java-25-orange?style=flat-square" alt="Java 25"></a>
  <a href="LICENSE.txt"><img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License: MIT"></a>
  <a href="https://github.com/f1owkang/Terrain-Diffusion-Next/graphs/contributors"><img src="https://img.shields.io/github/contributors/f1owkang/Terrain-Diffusion-Next?style=flat-square" alt="Contributors"></a>
</p>

<p align="center">
  <b>Diffusion-model terrain generation for Minecraft — AI-crafted mountains, rivers, coastlines and ore veins. Every world is one of a kind.</b>
</p>

<p align="center">
  [Download](https://github.com/f1owkang/Terrain-Diffusion-Next/releases) · [Quick start](#quick-start) · [Features](#features) · [Configuration](#configuration) · [FAQ](#faq) · [中文版](README.md)
</p>

**Terrain Diffusion Next** is an independent fork of [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) (targeting Minecraft 26.x) that integrates the [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) (SIGGRAPH '26) diffusion-model terrain generator into Minecraft, and extends it with rivers, beaches, aquifers, ore veins and vanilla structure support.

> ⚠️ **The current release is an early development release**: features are still under active iteration — bugs and rough edges are expected. For a stable experience, use the upstream 2.x builds (Minecraft 1.20.1 / 1.21.1 / 1.21.11).

## Features

| Feature | Description |
|---------|-------------|
| **AI terrain** | Three-stage diffusion models stream-generate tile by tile (coarse → latent → decoder), outputting elevation + climate variables. Determined by the world seed and reproducible |
| **River system** | Hybrid mode (default): D8 flow-accumulation paths follow the real terrain (computed on a halo-extended window so they stay seamless across tiles) and are carved below sea level so channels actually hold water; switchable back to pure noise carving (`rivers.mode=carver`) |
| **Beach biomes** | Coastline detection (land pixels adjacent to ocean near sea level), mapped to `BEACH` / `SNOWY_BEACH` / `STONY_SHORE` |
| **Aquifers** | Re-enabled `aquifers_enabled` and restored the four vanilla noise-router channels (barrier / fluid level / lava) — cave water bodies and lava layers are back |
| **Ore veins** | Re-enabled `ore_veins_enabled` and restored the three vein channels — large iron/copper vein deposits generate |
| **Structure support** | Biome tags added for the 3 custom biomes (forest_sparse / taiga_sparse / snowy_taiga_sparse); villages, mineshafts, strongholds etc. now generate on their chunks |
| **Localization** | Simplified Chinese language file; the "Terrain Diffusion Next" world type and settings screens display in Chinese |

## Screenshots

<!-- TODO: Add in-game screenshots. 2~3 suggested: terrain panorama, river system, caves/ore veins.

     Put the images under docs/screenshots/ and reference them here, e.g.:

     <img src="docs/screenshots/terrain.jpg" width="800" alt="Terrain panorama" />
     <img src="docs/screenshots/rivers.jpg" width="800" alt="River system" />
     <img src="docs/screenshots/caves.jpg" width="800" alt="Caves and ore veins" />
-->

## Quick start

```bash
# 1. Download the jar matching your Minecraft version from Releases and put it in your mods/ folder
# 2. Launch the game (once online) — the AI models (~2.5GB) download automatically
# 3. Create a world → select the "Terrain Diffusion Next" world type
# 4. Optional: click Customize to set the World Scale (see below)
```

**Requires Fabric + Fabric API.** The CPU build is slow unless you are on macOS — the Windows or CUDA builds are recommended:

| Build                     | Supports                    | Setup required                          |
|---------------------------| --------------------------- | --------------------------------------- |
| **Windows** (recommended) | Windows with any modern GPU | None                                    |
| **CUDA**                  | NVIDIA GPUs                 | [CUDA + cuDNN install](CUDA_INSTALL.md) |
| **CPU**                   | Everything else             | None (CoreML acceleration on Apple Silicon) |

**Requirements**: Java 25 · 1.5GB VRAM · 2.5GB RAM (may need to increase Minecraft's RAM allocation)

## Supported Minecraft versions

This mod targets Minecraft **26.x** (**26.1**, **26.2**, **26.3**). A single jar declares compatibility with the whole `>=26.1` range.

> For Minecraft 1.20.1 / 1.21.1 / 1.21.11 (the last obfuscated releases) use the upstream 2.x builds.

## Tips

**World Scale (per-world setting)**: click **Customize** in world creation and set `World Scale` (integer `1..6`). Saved with the world:

- how many real-world meters each block represents (`scale=1` => `30m/block`, `scale=2` => `15m/block`)
- 2 is recommended for a good balance of scale and playability; use 1 for smaller, more compressed worlds
- Lower values stress the GPU more, higher values stress the CPU more (larger world height)

**Exploring the world**: run `/td-explore` to open the built-in terrain explorer web UI (`http://localhost:19801`) and scout continents, mountains, rivers and islands before venturing out. A land spawn point near (0,0) is found automatically.

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

## FAQ

- **A dynamic link library (DLL) initialization routine failed** — requires Java 25 or higher; the [latest Microsoft OpenJDK 25](https://learn.microsoft.com/en-us/java/openjdk/download) is known to work
- **LoadLibrary failed with error 126** (CUDA build only) — improper CUDA/cuDNN installation; see [CUDA_INSTALL.md](CUDA_INSTALL.md)
- **Failed to load terrain-diffusion models** — out of memory; the models take up about 2.5GB of RAM, increase Minecraft's RAM allocation

**Issue still not resolved? [Raise it here](https://github.com/f1owkang/Terrain-Diffusion-Next/issues/new).**

## Development

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
<a href="https://github.com/deepseek-ai" title="DeepSeek (AI development assistant)"><img src="https://github.com/deepseek-ai.png" width="50" height="50" alt="DeepSeek" /></a>

### Upstream projects

- [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) — the diffusion-model terrain generator (SIGGRAPH '26 / InfiniteDiffusion)
- [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) — the upstream mod this project forks
- [infinite-tensor](https://github.com/xandergos/infinite-tensor) — the streaming tile tensor library
