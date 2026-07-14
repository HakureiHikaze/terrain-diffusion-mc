package com.github.xandergos.terraindiffusionmc.pipeline;

import ai.onnxruntime.*;
import ai.onnxruntime.providers.CoreMLFlags;
import ai.onnxruntime.providers.OrtCUDAProviderOptions;
import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin wrapper around ONNX Runtime with aggressive VRAM optimization.
 *
 * <p>Only one model is resident in GPU VRAM at a time (GPU-slot swapping).
 * Model weights are kept in CPU RAM between inference calls and uploaded to
 * GPU on demand. This keeps peak VRAM to a single model's footprint instead
 * of all three simultaneously.
 */
public final class OnnxModel implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OnnxModel.class);
    private static final String OPTIMIZED_MODELS_DIR_NAME = "onnx-cache";

    private static volatile String resolvedInferenceProvider = null;
    private static final AtomicBoolean providerLoggedOnce = new AtomicBoolean(false);
    // Accelerator slot: when offload_models=true, only one accelerated session is alive at a time.
    private static final Object ACCELERATOR_SLOT_LOCK = new Object();
    private static OnnxModel acceleratorSlotHolder = null;
    private static OrtSession activeAcceleratorSession = null;

    private final OrtEnvironment env;
    private final byte[] optimizedModelBytes;
    private final String name;
    private OrtSession cpuSession;
    private OrtSession acceleratorSession;
    private InferenceProviderSelector.Provider selectedProvider;
    private boolean fallbackToCpu;

    private static final class OptimizedModelLoadResult {
        private final byte[] modelBytes;
        private final Path optimizedModelPath;
        private final boolean loadedFromCache;

        private OptimizedModelLoadResult(byte[] modelBytes, Path optimizedModelPath, boolean loadedFromCache) {
            this.modelBytes = modelBytes;
            this.optimizedModelPath = optimizedModelPath;
            this.loadedFromCache = loadedFromCache;
        }
    }

    public OnnxModel(Path modelFilePath, String name) {
        this.name = name;
        try {
            long start = System.currentTimeMillis();
            this.env = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
            byte[] sourceModelBytes = Files.readAllBytes(modelFilePath);
            OptimizedModelLoadResult initialOptimizedModelLoadResult = optimizeModelAtRuntime(sourceModelBytes, false);
            byte[] loadedModelBytes;
            try {
                initializeModelSession(initialOptimizedModelLoadResult.modelBytes, start);
                loadedModelBytes = initialOptimizedModelLoadResult.modelBytes;
            } catch (Exception initialLoadException) {
                if (!initialOptimizedModelLoadResult.loadedFromCache) {
                    throw initialLoadException;
                }
                closeLoadedSessions();
                LOG.warn("Cached optimized ONNX model '{}' failed to load. Rebuilding cache: {}",
                        name, initialLoadException.getMessage());
                deleteOptimizedCacheFile(initialOptimizedModelLoadResult.optimizedModelPath);
                OptimizedModelLoadResult rebuiltOptimizedModelLoadResult = optimizeModelAtRuntime(sourceModelBytes, true);
                initializeModelSession(rebuiltOptimizedModelLoadResult.modelBytes, start);
                loadedModelBytes = rebuiltOptimizedModelLoadResult.modelBytes;
            }
            this.optimizedModelBytes = loadedModelBytes;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ONNX model: " + modelFilePath, e);
        }
    }

    /**
     * Optimizes model bytes and caches the optimized file in the config directory.
     * Falls back to the source model bytes if optimization or cache I/O fails.
     */
    private OptimizedModelLoadResult optimizeModelAtRuntime(byte[] sourceModelBytes, boolean forceRebuildFromSource) {
        Path optimizedModelPath = resolveOptimizedModelPath(sourceModelBytes);
        try {
            if (!forceRebuildFromSource && Files.exists(optimizedModelPath)) {
                byte[] cachedOptimizedModelBytes = Files.readAllBytes(optimizedModelPath);
                return new OptimizedModelLoadResult(cachedOptimizedModelBytes, optimizedModelPath, true);
            }

            Files.createDirectories(optimizedModelPath.getParent());
            Path temporaryOptimizedModelPath = optimizedModelPath.resolveSibling(optimizedModelPath.getFileName() + ".tmp");
            Files.deleteIfExists(temporaryOptimizedModelPath);
            OrtSession.SessionOptions optimizationOptions = new OrtSession.SessionOptions();
            try {
                optimizationOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
                optimizationOptions.setOptimizedModelFilePath(temporaryOptimizedModelPath.toAbsolutePath().toString());
                try (OrtSession ignored = env.createSession(sourceModelBytes, optimizationOptions)) {
                    // Session creation materializes the optimized model on disk.
                }
            } finally {
                optimizationOptions.close();
            }
            byte[] optimizedModelBytesFromDisk = Files.readAllBytes(temporaryOptimizedModelPath);
            Files.move(
                    temporaryOptimizedModelPath,
                    optimizedModelPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
            LOG.info("Optimized ONNX model '{}' at runtime ({} KB -> {} KB)",
                    name, sourceModelBytes.length / 1024, optimizedModelBytesFromDisk.length / 1024);
            return new OptimizedModelLoadResult(optimizedModelBytesFromDisk, optimizedModelPath, false);
        } catch (Exception optimizationException) {
            LOG.warn("Runtime ONNX optimization failed for '{}', using source model bytes: {}",
                    name, optimizationException.getMessage());
            return new OptimizedModelLoadResult(sourceModelBytes, optimizedModelPath, false);
        }
    }

    /** Returns the resolved inference provider name, or {@code "unknown"} if not yet determined. */
    public static String getResolvedInferenceProvider() {
        String provider = resolvedInferenceProvider;
        return provider != null ? provider : "unknown";
    }

    private static void setResolvedProviderOnce(String provider) {
        if (resolvedInferenceProvider == null) {
            resolvedInferenceProvider = provider;
        }
        if (providerLoggedOnce.compareAndSet(false, true)) {
            LOG.info("Terrain diffusion inference: provider={}", provider);
        }
    }

    /**
     * Loads model sessions for the active inference device configuration.
     */
    private void initializeModelSession(byte[] modelBytes, long startMillis) throws OrtException {
        InferenceProviderSelector.BuildVariant buildVariant = InferenceProviderSelector.buildVariant(
                TerrainDiffusionConfig.buildVariant());
        InferenceProviderSelector.OperatingSystem os = InferenceProviderSelector.operatingSystem(System.getProperty("os.name"));
        String requestedDevice = TerrainDiffusionConfig.inferenceDevice();
        InferenceProviderSelector.Decision decision = InferenceProviderSelector.resolve(buildVariant, os, requestedDevice);
        LOG.info("Terrain diffusion inference: os={}, buildVariant={}, requested={}, selected={}",
                os, buildVariant, requestedDevice, decision.preferredProvider());

        if (!decision.usesAccelerator()) {
            createCpuSession(modelBytes, startMillis);
            return;
        }
        fallbackToCpu = !decision.acceleratorRequired();
        try {
            if (decision.preferredProvider() == InferenceProviderSelector.Provider.COREML && !TerrainDiffusionConfig.offloadModels()) {
                throw new OrtException("inference.offload_models=false is not supported with CoreML. Set it to true.");
            }
            if (TerrainDiffusionConfig.offloadModels()) {
                // Verify the native provider now so auto can fall back before any world generation.
                try (OrtSession probe = createAcceleratedSession(modelBytes, decision.preferredProvider())) {
                    // The real session is created on demand and remains subject to slot offloading.
                }
                selectedProvider = decision.preferredProvider();
                cpuSession = null;
                acceleratorSession = null;
                setResolvedProviderOnce(selectedProvider.name());
                LOG.info("ONNX model '{}' bytes cached for {} offloading ({} KB) in {} ms", name,
                        selectedProvider, modelBytes.length / 1024, System.currentTimeMillis() - startMillis);
            } else {
                acceleratorSession = createAcceleratedSession(modelBytes, decision.preferredProvider());
                selectedProvider = decision.preferredProvider();
                setResolvedProviderOnce(selectedProvider.name());
                LOG.info("ONNX model '{}' loaded with {} ({} KB) in {} ms", name, selectedProvider,
                        modelBytes.length / 1024, System.currentTimeMillis() - startMillis);
            }
        } catch (OrtException | UnsatisfiedLinkError | NoClassDefFoundError providerFailure) {
            if (decision.acceleratorRequired()) throw requiredProviderFailure(decision.preferredProvider(), providerFailure);
            LOG.warn("{} provider unavailable for auto mode; using CPU: {}", decision.preferredProvider(), providerFailure.getMessage());
            createCpuSession(modelBytes, startMillis);
        }
    }

    private void createCpuSession(byte[] modelBytes, long startMillis) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        try {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            cpuSession = env.createSession(modelBytes, options);
        } finally {
            options.close();
        }
        acceleratorSession = null;
        selectedProvider = InferenceProviderSelector.Provider.CPU;
        setResolvedProviderOnce("CPU");
        LOG.info("ONNX model '{}' loaded on CPU ({} KB) in {} ms", name, modelBytes.length / 1024,
                System.currentTimeMillis() - startMillis);
    }

    private static OrtException requiredProviderFailure(InferenceProviderSelector.Provider provider, Throwable cause) {
        String platform = System.getProperty("os.name");
        String details = provider == InferenceProviderSelector.Provider.CUDA
                ? " The CUDA artifact was loaded, but CUDA and cuDNN native libraries/shared libraries could not be loaded. " +
                "Install the required CUDA dependencies or use the CPU artifact. On Linux see CUDA_INSTALL.md; check LD_LIBRARY_PATH and .so libraries."
                : " The required " + provider + " provider could not be loaded. Use the matching artifact or set inference.device=cpu.";
        OrtException failure = new OrtException("inference.device=gpu requires " + provider + " on " + platform + "." + details + " Cause: " + cause.getMessage());
        failure.initCause(cause);
        return failure;
    }

    private void closeLoadedSessions() {
        if (cpuSession != null) {
            try { cpuSession.close(); } catch (OrtException ignored) {}
            cpuSession = null;
        }
        if (acceleratorSession != null) {
            try { acceleratorSession.close(); } catch (OrtException ignored) {}
            acceleratorSession = null;
        }
    }

    private void deleteOptimizedCacheFile(Path optimizedModelPath) {
        try {
            Files.deleteIfExists(optimizedModelPath);
        } catch (Exception deleteException) {
            LOG.warn("Failed to delete optimized cache '{}' for '{}': {}",
                    optimizedModelPath, name, deleteException.getMessage());
        }
    }

    /**
     * Resolves a deterministic cache file path for an optimized model.
     */
    private Path resolveOptimizedModelPath(byte[] sourceModelBytes) {
        String sourceModelHashPrefix = sha256Hex(sourceModelBytes).substring(0, 16);
        String runtimeVersionTag = resolveOnnxRuntimeVersionTag();
        String optimizedFileName = name + "-" + runtimeVersionTag + "-" + sourceModelHashPrefix + ".onnx";
        return ModelAssetManager.resolveAssetPath(OPTIMIZED_MODELS_DIR_NAME)
                .resolve(optimizedFileName);
    }

    /**
     * Returns the ONNX Runtime version used as part of the optimization cache key.
     */
    private static String resolveOnnxRuntimeVersionTag() {
        Package onnxRuntimePackage = OrtEnvironment.class.getPackage();
        String implementationVersion = onnxRuntimePackage == null ? null : onnxRuntimePackage.getImplementationVersion();
        return implementationVersion == null ? "unknown" : implementationVersion;
    }

    /**
     * Computes a lowercase SHA-256 hex string for deterministic cache naming.
     */
    private static String sha256Hex(byte[] inputBytes) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digestBytes = messageDigest.digest(inputBytes);
            StringBuilder hexBuilder = new StringBuilder(digestBytes.length * 2);
            for (byte digestByte : digestBytes) {
                hexBuilder.append(String.format("%02x", digestByte));
            }
            return hexBuilder.toString();
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException("Missing SHA-256 algorithm", noSuchAlgorithmException);
        }
    }

    /**
     * Run the model with a flat float array for each named input.
     * Each entry in {@code inputs} is (name, float[] data, long[] shape).
     *
     * @return the output tensor as a flat float array
     */
    public float[] run(Object[][] inputs) {
        if (cpuSession != null) {
            return runWithSession(cpuSession, inputs);
        }
        if (acceleratorSession != null) {
            return runWithSession(acceleratorSession, inputs);
        }
        synchronized (ACCELERATOR_SLOT_LOCK) {
            claimAcceleratorSlot();
            return runWithSession(activeAcceleratorSession, inputs);
        }
    }

    /** Convenience: run with x, noise_labels, and optional cond tensors. */
    public float[] runModel(float[] x, long[] xShape,
                            float[] noiseLabels,
                            float[][] condInputs, long[][] condShapes) {
        int nCond = condInputs == null ? 0 : condInputs.length;
        Object[][] inputs = new Object[2 + nCond][3];
        inputs[0] = new Object[]{"x", x, xShape};
        inputs[1] = new Object[]{"noise_labels", noiseLabels, new long[]{noiseLabels.length}};
        for (int i = 0; i < nCond; i++)
            inputs[2 + i] = new Object[]{"cond_" + i, condInputs[i], condShapes[i]};
        return run(inputs);
    }

    /**
     * Evicts the current accelerated session if this model doesn't hold the slot,
     * then creates a fresh accelerated session from CPU-cached weights.
     * Must be called under ACCELERATOR_SLOT_LOCK.
     */
    private void claimAcceleratorSlot() {
        if (acceleratorSlotHolder == this) return;

        if (activeAcceleratorSession != null) {
            LOG.debug("Evicting '{}' from {}, loading '{}'", acceleratorSlotHolder != null ? acceleratorSlotHolder.name : "?",
                    acceleratorSlotHolder != null ? acceleratorSlotHolder.selectedProvider : "accelerator", name);
            try { activeAcceleratorSession.close(); } catch (OrtException ignored) {}
            activeAcceleratorSession = null;
            acceleratorSlotHolder = null;
        }

        try {
            activeAcceleratorSession = createAcceleratedSession(optimizedModelBytes, selectedProvider);
            acceleratorSlotHolder = this;
            LOG.debug("{} session ready for '{}'", selectedProvider, name);
        } catch (OrtException | UnsatisfiedLinkError | NoClassDefFoundError e) {
            if (fallbackToCpu) {
                LOG.warn("{} provider became unavailable in auto mode; switching '{}' to CPU: {}",
                        selectedProvider, name, e.getMessage());
                try {
                    createCpuSession(optimizedModelBytes, System.currentTimeMillis());
                } catch (OrtException cpuFailure) {
                    throw new RuntimeException("Failed to create CPU fallback session for: " + name, cpuFailure);
                }
                return;
            }
            throw new RuntimeException("Failed to create " + selectedProvider + " session for: " + name, e);
        }
    }

    private OrtSession createAcceleratedSession(byte[] modelBytes, InferenceProviderSelector.Provider provider) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        try {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            addProvider(options, provider);
            return env.createSession(modelBytes, options);
        } finally {
            options.close();
        }
    }

    private static void addProvider(OrtSession.SessionOptions options, InferenceProviderSelector.Provider provider) throws OrtException {
        switch (provider) {
            case CUDA -> {
                OrtCUDAProviderOptions cudaOpts = new OrtCUDAProviderOptions(0);
                try {
            // Only grow the BFC arena by exactly what is needed, never pre-allocate.
            cudaOpts.add("arena_extend_strategy", "kSameAsRequested");
            // Heuristic: fast startup, no exhaustive benchmarking, workspace-efficient.
            cudaOpts.add("cudnn_conv_algo_search", "HEURISTIC");
            cudaOpts.add("do_copy_in_default_stream", "1");
                    options.addCUDA(cudaOpts);
                } finally {
                    cudaOpts.close();
                }
            }
            case DIRECTML -> options.addDirectML(0);
            case COREML -> options.addCoreML(EnumSet.of(CoreMLFlags.ENABLE_ON_SUBGRAPH));
            case CPU -> { }
            }
    }

    private static float[] runWithSession(OrtSession session, Object[][] inputs) {
        Map<String, OnnxTensor> feed = new LinkedHashMap<>();
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try {
            for (Object[] inp : inputs) {
                feed.put((String) inp[0],
                        OnnxTensor.createTensor(env, FloatBuffer.wrap((float[]) inp[1]), (long[]) inp[2]));
            }
            try (OrtSession.Result result = session.run(feed)) {
                OnnxTensor output = (OnnxTensor) result.get(0);
                FloatBuffer buf = output.getFloatBuffer();
                float[] out = new float[buf.remaining()];
                buf.get(out);
                return out;
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX inference failed", e);
        } finally {
            for (OnnxTensor t : feed.values()) t.close();
        }
    }

    @Override
    public void close() {
        synchronized (ACCELERATOR_SLOT_LOCK) {
            if (acceleratorSlotHolder == this && activeAcceleratorSession != null) {
                try { activeAcceleratorSession.close(); } catch (OrtException ignored) {}
                activeAcceleratorSession = null;
                acceleratorSlotHolder = null;
            }
        }
        if (cpuSession != null) {
            try { cpuSession.close(); } catch (OrtException ignored) {}
            cpuSession = null;
        }
        if (acceleratorSession != null) {
            try { acceleratorSession.close(); } catch (OrtException ignored) {}
            acceleratorSession = null;
        }
    }
}
