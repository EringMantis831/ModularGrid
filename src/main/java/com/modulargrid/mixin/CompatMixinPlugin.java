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
 */
public class CompatMixinPlugin implements IMixinConfigPlugin {

    private static final String CODE_CHICKEN_LIB_MIXIN = "com.modulargrid.mixin.CodeChickenLibReflectionMixin";
    private static final String NOVAENG_STARLIGHT_MIXIN = "com.modulargrid.mixin.NovaEngStarlightAdapterMixin";
    private static final String CODE_CHICKEN_LIB_RESOURCE = "codechicken/lib/CodeChickenLib.class";
    private static final String NOVAENG_ADAPTER_RESOURCE =
            "github/kasuminova/novaeng/common/adapter/astralsorcery/AdapterStarlightInfuser.class";
    private static final String ASTRAL_RECIPE_REGISTRY_RESOURCE =
            "hellfirepvp/astralsorcery/common/crafting/infusion/InfusionRecipeRegistry.class";
    private static final String ASTRAL_ACCESS_MANAGER_RESOURCE =
            "hellfirepvp/astralsorcery/common/crafting/helper/CraftingAccessManager.class";

    private static Boolean codeChickenLibPresent;
    private static Boolean novaEngStarlightPresent;

    @Override
    public void onLoad(String mixinPackage) {
        // Nothing is decided here on purpose: this runs while Mixin selects configs, which is before
        // FML has loaded any mod, so neither the mod list nor CodeChickenLib's classes exist yet.
    }

    /**
     * Answers lazily, because Mixin asks this once per target class - long after mod loading started.
     */
    private static boolean isPresent(String resource) {
        InputStream probe = null;
        try {
            probe = CompatMixinPlugin.class.getClassLoader().getResourceAsStream(resource);
            return probe != null;
        } catch (Throwable absent) {
            return false;
        } finally {
            if (probe != null) {
                try {
                    probe.close();
                } catch (IOException ignored) {
                    // nothing to do about a failed close of a resource that was only probed
                }
            }
        }
    }

    private static boolean isCodeChickenLibPresent() {
        Boolean known = codeChickenLibPresent;
        if (known == null) {
            known = isPresent(CODE_CHICKEN_LIB_RESOURCE);
            codeChickenLibPresent = known;
        }
        return known;
    }

    /**
     * Both mods have to be there: the mixin repairs a Nova Engineering Core class and needs Astral Sorcery's recipe
     * registry with its {@code CraftingAccessManager} to be able to compile that registry early.
     */
    private static boolean isNovaEngStarlightIntegrationPresent() {
        Boolean known = novaEngStarlightPresent;
        if (known == null) {
            known = isPresent(NOVAENG_ADAPTER_RESOURCE)
                    && isPresent(ASTRAL_RECIPE_REGISTRY_RESOURCE)
                    && isPresent(ASTRAL_ACCESS_MANAGER_RESOURCE);
            novaEngStarlightPresent = known;
        }
        return known;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (CODE_CHICKEN_LIB_MIXIN.equals(mixinClassName)) {
            return isCodeChickenLibPresent();
        }
        if (NOVAENG_STARLIGHT_MIXIN.equals(mixinClassName)) {
            return isNovaEngStarlightIntegrationPresent();
        }
        return false;
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
