package com.modulargrid.mixin;

import com.modulargrid.common.compat.AstralRecipes;
import crafttweaker.util.IEventHandler;
import github.kasuminova.mmce.common.event.recipe.RecipeEvent;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentRequirement;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fixes Nova Engineering Core's starlight infuser adapter reading Astral Sorcery's infusion recipes too early.
 */
@Mixin(targets = "github.kasuminova.novaeng.common.adapter.astralsorcery.AdapterStarlightInfuser", remap = false)
public abstract class NovaEngStarlightAdapterMixin {

    @Inject(method = "createRecipesFor", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void modulargrid$compileAstralRecipesBeforeReadingThem(
            ResourceLocation owningMachineName,
            List<RecipeModifier> modifiers,
            List<ComponentRequirement<?, ?>> additionalRequirements,
            Map<Class<?>, List<IEventHandler<RecipeEvent>>> eventHandlers,
            List<String> recipeTooltips,
            CallbackInfoReturnable<Collection<MachineRecipe>> callbackInfo) {
        if (AstralRecipes.ensureCompiled()) {
            return;
        }

        callbackInfo.setReturnValue(Collections.emptyList());
    }
}
