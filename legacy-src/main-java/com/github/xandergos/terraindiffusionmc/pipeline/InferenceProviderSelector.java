package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.Locale;
import java.util.List;

/** Pure build/platform/config policy for selecting an ONNX Runtime provider. */
public final class InferenceProviderSelector {
    public enum OperatingSystem { WINDOWS, MACOS, LINUX, OTHER }
    public enum BuildVariant { DML, CUDA, CPU }
    public enum Provider { CPU, DIRECTML, CUDA, COREML }

    public record Decision(List<Provider> providers, boolean acceleratorRequired) {
        public Decision {
            providers = List.copyOf(providers);
        }
        public Provider preferredProvider() { return providers.getFirst(); }
        public boolean usesAccelerator() { return preferredProvider() != Provider.CPU; }
    }

    private InferenceProviderSelector() {}

    public static OperatingSystem operatingSystem(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("windows")) return OperatingSystem.WINDOWS;
        if (normalized.startsWith("mac") || normalized.startsWith("darwin")) return OperatingSystem.MACOS;
        if (normalized.contains("linux")) return OperatingSystem.LINUX;
        return OperatingSystem.OTHER;
    }

    public static BuildVariant buildVariant(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
            case "windows", "dml", "directml" -> BuildVariant.DML;
            case "cuda" -> BuildVariant.CUDA;
            case "cpu" -> BuildVariant.CPU;
            default -> throw new IllegalArgumentException("Unknown embedded build variant: " + value);
        };
    }

    public static String defaultDevice(BuildVariant variant) {
        return variant == BuildVariant.CPU ? "auto" : "gpu";
    }

    public static Decision resolve(BuildVariant variant, OperatingSystem os, String device) {
        String mode = device == null ? "" : device.trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("cpu") && !mode.equals("gpu") && !mode.equals("auto")) {
            throw new IllegalArgumentException("Invalid inference.device='" + device + "'. Use cpu, gpu, or auto.");
        }
        if (mode.equals("cpu")) return new Decision(List.of(Provider.CPU), false);

        if (variant == BuildVariant.DML && os != OperatingSystem.WINDOWS) {
            throw new IllegalStateException("The DirectML artifact is Windows-only and cannot provide GPU inference on " + os + ".");
        }

        Provider accelerator = switch (variant) {
            case DML -> Provider.DIRECTML;
            case CUDA -> Provider.CUDA;
            case CPU -> os == OperatingSystem.MACOS ? Provider.COREML : Provider.CPU;
        };
        if (accelerator == Provider.CPU) {
            if (mode.equals("gpu")) {
                throw new IllegalStateException("inference.device=gpu requires an accelerator, but this CPU artifact has no GPU provider on " + os + ". Use inference.device=cpu or auto.");
            }
            return new Decision(List.of(Provider.CPU), false);
        }
        return new Decision(mode.equals("gpu") ? List.of(accelerator) : List.of(accelerator, Provider.CPU),
                mode.equals("gpu"));
    }
}
