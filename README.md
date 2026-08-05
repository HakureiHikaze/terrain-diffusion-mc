# Terrain Diffusion Next

<p align="center">
  <img src="site/assets/logo.svg" width="120" height="120" alt="Terrain Diffusion Next" title="Terrain Diffusion Next">
</p>

<p align="center">
  <em>项目图标为 AI 生成，欢迎社区投稿更佳设计 —— 可在 <a href="https://github.com/f1owkang/Terrain-Diffusion-Next/issues/new">Issue</a> 中提交。</em>
</p>

<p align="center">
  <a href="https://github.com/f1owkang/Terrain-Diffusion-Next/releases"><img src="https://img.shields.io/badge/Minecraft-26.x-brightgreen?style=flat-square" alt="Minecraft 26.x"></a>
  <a href=""><img src="https://img.shields.io/badge/Java-25-orange?style=flat-square" alt="Java 25"></a>
  <a href="LICENSE.txt"><img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License: MIT"></a>
  <a href="https://github.com/f1owkang/Terrain-Diffusion-Next/graphs/contributors"><img src="https://img.shields.io/github/contributors/f1owkang/Terrain-Diffusion-Next?style=flat-square" alt="Contributors"></a>
</p>

<p align="center">
  <b>把扩散模型地形生成器装进 Minecraft —— AI 生成的山脉、河流、海岸与矿脉，每张地图都独一无二。</b>
</p>

<p align="center">
  [下载](https://github.com/f1owkang/Terrain-Diffusion-Next/releases) · [快速开始](#快速开始) · [功能](#功能) · [配置](#配置) · [常见问题](#常见问题) · [English](README_en.md)
</p>

**Terrain Diffusion Next** 是 [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) 的独立 fork（面向 Minecraft 26.x）：将 [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion)（SIGGRAPH '26）扩散模型地形生成器集成进 Minecraft，并在此基础上扩展了河流、海滩、含水层、矿脉与原版结构支持。

> ⚠️ **当前为早期开发版**：新特性持续迭代，可能存在 bug。需要稳定体验请使用上游 2.x 稳定版（Minecraft 1.20.1 / 1.21.1 / 1.21.11）。

## 功能

| 功能 | 说明 |
|------|------|
| **AI 地形** | 三阶段扩散模型逐 tile 流式生成（粗 → 潜空间 → 解码），输出高程 + 气候变量，由世界种子决定，可复现 |
| **河流系统** | hybrid 模式（默认）：D8 汇流路径沿真实地形走向（带 halo 窗口跨 tile 无缝），雕刻至海平面下真实蓄水；可切换纯噪声雕刻（`rivers.mode=carver`） |
| **海滩生物群系** | 海岸带检测（陆地像素邻接海洋且近海平面），映射 `BEACH` / `SNOWY_BEACH` / `STONY_SHORE` |
| **含水层** | 重新启用 `aquifers_enabled`，恢复 barrier / fluid_level / lava 四条原版 noise_router 通道，洞穴内出现水体与熔岩层 |
| **矿脉** | 重新启用 `ore_veins_enabled`，恢复 vein 三条通道，生成大型铁矿/铜矿脉 |
| **结构支持** | 为 3 个自定义生物群系（forest_sparse / taiga_sparse / snowy_taiga_sparse）补齐 biome tag，村庄、矿井、要塞等原版结构可在其区块生成 |
| **界面本地化** | 简体中文语言文件，世界创建界面的"地形扩散·Next"世界类型与设置界面中文显示 |

## 截图

<!-- TODO: 待补充游戏内截图。建议 2~3 张：地形全景、河流水系、洞穴/矿脉。

     图片文件建议放在 docs/screenshots/ 目录下，在此处用 Markdown 引用，例如：

     <img src="docs/screenshots/terrain.jpg" width="800" alt="地形全景" />
     <img src="docs/screenshots/rivers.jpg" width="800" alt="河流水系" />
     <img src="docs/screenshots/caves.jpg" width="800" alt="洞穴与矿脉" />
-->

## 快速开始

```bash
# 1. 从 Releases 下载与你的 Minecraft 版本匹配的 jar，放入 mods/ 文件夹
# 2. 启动游戏（需在线一次），自动下载 AI 模型（约 2.5GB）
# 3. 创建世界 → 世界类型选择「地形扩散·Next」
# 4. 可选：点击 Customize 设置 World Scale（见下文）
```

**需要 Fabric + Fabric API。** 除非你在 macOS 上，否则 CPU 构建很慢，推荐使用 Windows 或 CUDA 构建：

| 构建 | 支持环境 | 需要配置 |
|---------------------------| --------------------------- | --------------------------------------- |
| **Windows**（推荐） | 任意现代显卡的 Windows | 无 |
| **CUDA** | NVIDIA 显卡 | [安装 CUDA + cuDNN](CUDA_INSTALL.md) |
| **CPU** | 其他一切环境 | 无（Apple Silicon 自动使用 CoreML 加速） |

**环境要求**：Java 25 · 显存 1.5GB · 内存 2.5GB（可能需要调高 Minecraft 内存分配）

## 支持的 Minecraft 版本

本 Mod 面向 Minecraft **26.x**（**26.1**、**26.2**、**26.3**）。单个 jar 声明兼容整个 `>=26.1` 范围。

> 对于 Minecraft 1.20.1 / 1.21.1 / 1.21.11（最后一批混淆版本），请使用上游的 2.x 构建。

## 使用技巧

**World Scale（每世界设置）**：世界创建界面点击 **Customize** 设置 `World Scale`（整数 `1..6`），随存档保存：

- 每个方块代表多少真实世界米（`scale=1` 即 `30m/方块`，`scale=2` 即 `15m/方块`）
- 2 是平衡尺度与可玩性的推荐值；想要更小、更紧凑的世界用 1
- 更小的值对 GPU 压力更大，更大的值对 CPU 压力更大（世界高度更高）

**探索世界**：执行 `/td-explore` 打开内置地形探索网页（`http://localhost:19801`），提前侦察大陆、山脉、河流、岛屿，再动身出发。出生点会自动寻找 (0,0) 附近陆地。

## 配置

编辑 `config/terrain-diffusion-next.properties`（首次启动自动创建，每条配置均含中英双语备注）：

```
inference.device=gpu           # 推理设备：cpu / gpu / auto（优先 GPU，失败回退 CPU）
inference.offload_models=true  # 将非活动模型移出显存，GPU 同一时刻只驻留一个模型
validate_model=true            # 对已下载的模型文件做 SHA-256 校验
explorer.port=19801            # 地形探索器网页界面端口（/td-explore 启动）
tile_size=256                  # 地形生成区域的边长（方块数），必须是 2 的幂
spawn_search.initial_size=16   # 出生点搜索：在 (0,0) 附近检测陆地的粗像素区域大小
spawn_search.max_size=128
```

## 常见问题

- **动态链接库（DLL）初始化例程失败** — 需要 Java 25 或更高版本，已知[最新版 Microsoft OpenJDK 25](https://learn.microsoft.com/en-us/java/openjdk/download) 可用
- **LoadLibrary failed with error 126**（仅 CUDA 构建）— CUDA 或 cuDNN 安装不当，排查步骤见 [CUDA_INSTALL.md](CUDA_INSTALL.md)
- **Failed to load terrain-diffusion models** — 内存不足，模型约占 2.5GB，请调高 Minecraft 内存分配

**问题仍未解决？[在此提交 issue](https://github.com/f1owkang/Terrain-Diffusion-Next/issues/new)。**

## 开发

构建方法、DirectML onnxruntime 编译及 Mod 开发说明见 [BUILDING.md](BUILDING.md)。

## 贡献者

本项目是 [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) 的 fork，Terrain Diffusion 生态由以下贡献者共同打造（排名不分先后）：

<a href="https://github.com/xandergos"><img src="https://github.com/xandergos.png" width="50" height="50" alt="xandergos" title="xandergos" /></a>
<a href="https://github.com/AirRunner"><img src="https://github.com/AirRunner.png" width="50" height="50" alt="AirRunner" title="AirRunner" /></a>
<a href="https://github.com/ThatDamnWittyWhizHard"><img src="https://github.com/ThatDamnWittyWhizHard.png" width="50" height="50" alt="ThatDamnWittyWhizHard" title="ThatDamnWittyWhizHard" /></a>
<a href="https://github.com/ayushsucksaf"><img src="https://github.com/ayushsucksaf.png" width="50" height="50" alt="ayushsucksaf" title="ayushsucksaf" /></a>
<a href="https://github.com/BillGoldenWater"><img src="https://github.com/BillGoldenWater.png" width="50" height="50" alt="BillGoldenWater" title="BillGoldenWater" /></a>
<a href="https://github.com/tlhr"><img src="https://github.com/tlhr.png" width="50" height="50" alt="tlhr" title="tlhr" /></a>
<a href="https://github.com/deforcy"><img src="https://github.com/deforcy.png" width="50" height="50" alt="deforcy" title="deforcy" /></a>
<a href="https://github.com/f1owkang"><img src="https://github.com/f1owkang.png" width="50" height="50" alt="f1owkang" title="f1owkang" /></a>
<a href="https://github.com/deepseek-ai" title="DeepSeek（AI 开发助手）"><img src="https://github.com/deepseek-ai.png" width="50" height="50" alt="DeepSeek" /></a>

### 上游项目

- [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) — 扩散模型地形生成项目（SIGGRAPH '26 / InfiniteDiffusion）
- [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) — 本 fork 的上游 Mod 项目
- [infinite-tensor](https://github.com/xandergos/infinite-tensor) — 流式 tile 张量库
