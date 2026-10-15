package com.modulargrid.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Enables {@code mixins.modulargrid.compat.json} only when CodeChickenLib is installed.
 *
 * <p>The compatibility mixins in that config patch CodeChickenLib classes, so loading them without
 * the mod would fail outright. ModularGrid itself does not need CodeChickenLib at all; the config
 * exists because Thermal Expansion (a common pack neighbour and a convenient way to feed energy into
 * an access plug) depends on it.</p>
 */
public class CompatMixinPlugin implements IMixinConfigPlugin {

    private static final String PROBE_RESOURCE = "codechicken/lib/CodeChickenLib.class";

    private static Boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        // Nothing is decided here on purpose: this runs while Mixin selects configs, which is before
        // FML has loaded any mod, so neither the mod list nor CodeChickenLib's classes exist yet.
    }

    /**
     * Answers lazily, because Mixin asks this once per target class - long after mod loading started.
     *
     * <p>The class file is looked up as a resource rather than with {@code Class.forName}: loading a
     * CodeChickenLib class while the transformer is still warming up leaves it in Cleanroom's
     * "invalid classes" set, and every later access to it then fails.</p>
     */
    private static boolean isCodeChickenLibPresent() {
        Boolean known = present;
        if (known == null) {
            InputStream probe = null;
            try {
                probe = CompatMixinPlugin.class.getClassLoader().getResourceAsStream(PROBE_RESOURCE);
                known = probe != null;
            } catch (Throwable absent) {
                known = false;
            } finally {
                if (probe != null) {
                    try {
                        probe.close();
                    } catch (IOException ignored) {
                        // nothing to do about a failed close of a resource that was only probed
                    }
                }
            }
            present = known;
        }
        return known;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return isCodeChickenLibPresent();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
