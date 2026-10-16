package com.modulargrid.mixin;

import com.modulargrid.common.compat.AdapterFailures;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.RecipeLoader;
import hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapterAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Isolates every recipe adapter Modular Machinery loads, so one broken adapter cannot stop mod loading.
 *
 * <p>{@code RecipeLoader#loadAdapterRecipes} runs each adapter's {@code createRecipesFor} during Modular Machinery's
 * {@code postInit} - both for adapters described by JSON and for those a script created through
 * {@code RecipeAdapterBuilder}. An exception there aborts the whole phase and is reported against Modular Machinery,
 * which is how a bug in a third-party adapter (or in a pack's adapter JSON) becomes an unstartable pack.</p>
 *
 * <p>Wrapping just that call keeps the blast radius at one adapter: the failing one is logged and skipped, the others
 * still load, and Modular Machinery re-runs all of them after CraftTweaker has loaded its scripts, so a transient
 * failure still recovers. See {@link AdapterFailures}.</p> *
 * <p>{@code require = 0} on purpose: Modular Machinery is a required dependency of ModularGrid, but its internals are
 * not a stable API - if a future release stops calling the adapter this way, the mixin stays out of the way instead of
 * failing to apply.</p>
 */
@Mixin(value = RecipeLoader.class, remap = false)
public class RecipeAdapterIsolationMixin {

    @Redirect(
            method = "loadAdapterRecipes",
            at = @At(
                    value = "INVOKE",
                    target = "Lhellfirepvp/modularmachinery/common/crafting/adapter/RecipeAdapterAccessor;"
                            + "loadRecipesForAdapter()Ljava/util/List;"
            ),
            require = 0,
            remap = false
    )
    private static List<MachineRecipe> modulargrid$isolateAdapterFailure(RecipeAdapterAccessor accessor) {
        return AdapterFailures.loadSafely(accessor, accessor::loadRecipesForAdapter);
    }
}
