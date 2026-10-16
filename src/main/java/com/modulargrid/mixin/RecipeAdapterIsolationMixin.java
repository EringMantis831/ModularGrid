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
