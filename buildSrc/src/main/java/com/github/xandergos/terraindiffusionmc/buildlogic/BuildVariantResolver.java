package com.github.xandergos.terraindiffusionmc.buildlogic;

import java.util.Locale;

/** Build-configuration policy used directly by the root Gradle build. */
public final class BuildVariantResolver {
    public enum OperatingSystem { WINDOWS, MACOS, LINUX, OTHER }
    public enum Variant { DML, CUDA, CPU }
    public record Selection(Variant variant) { }

    private BuildVariantResolver() { }

    public static OperatingSystem operatingSystem(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("windows")) return OperatingSystem.WINDOWS;
        if (normalized.startsWith("mac") || normalized.startsWith("darwin")) return OperatingSystem.MACOS;
        if (normalized.contains("linux")) return OperatingSystem.LINUX;
        return OperatingSystem.OTHER;
    }

    public static Selection resolve(OperatingSystem os, boolean cuda, boolean dml, boolean cpu) {
        int selected = (cuda ? 1 : 0) + (dml ? 1 : 0) + (cpu ? 1 : 0);
        if (selected > 1) throw new IllegalArgumentException("Only one of -PuseCuda, -PuseDml, or -PuseCpu may be specified at a time.");
        Variant variant = cuda ? Variant.CUDA : dml ? Variant.DML : cpu ? Variant.CPU
                : os == OperatingSystem.WINDOWS ? Variant.DML : Variant.CPU;
        if (variant == Variant.DML && os != OperatingSystem.WINDOWS) {
            throw new IllegalArgumentException("DirectML is Windows-only. Use the CPU artifact or -PuseCuda=true.");
        }
        return new Selection(variant);
    }
}
