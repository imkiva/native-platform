package gradlebuild;

import org.gradle.api.Action;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.platform.base.PlatformContainer;

public interface NativeRulesUtils {
    static void addPlatform(PlatformContainer platformContainer, String name, String os, String architecture) {
        if (platformContainer.stream().anyMatch(p -> p.getName().equals(name))) return;
        platformContainer.create(name, NativePlatform.class, platform -> {
            platform.operatingSystem(os);
            platform.architecture(architecture);
        });
    }

    static <T extends NativeToolChain> void addToolchain(NativeToolChainRegistry toolChainRegistry, String name, Class<T> type, Action<? super T> initializer) {
        if (toolChainRegistry.stream().anyMatch(p -> p.getName().equals(name))) return;
        toolChainRegistry.create(name, type, initializer);
    }
}
