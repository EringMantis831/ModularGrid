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
 *
 * <p>The adapter counts recipes from {@code InfusionRecipeRegistry#recipes} plus {@code #mtRecipes}, but reads every
 * recipe through {@code InfusionRecipeRegistry#getRecipe(int)}, which indexes the {@code compiledRecipes} array. That
 * array is only built by {@code CraftingAccessManager#compile()} in Astral Sorcery's {@code postInit}, while Modular
 * Machinery generates adapter recipes in <em>its</em> {@code postInit} - a mod order in which Modular Machinery comes
 * first makes the adapter throw {@code NullPointerException: ... compiledRecipes is null} and takes the whole game
 * down with it (the failing mod is reported as Modular Machinery, since it is the mod being initialised).</p>
 *
 * <p>Both lists are already final at that point - every mod's {@code init} has run - so the missing step is only the
 * fold into an array. This injects before the adapter reads anything and asks Astral Sorcery to do that fold, which
 * leaves the adapter with a compiled array exactly like it would have had if the mods had been ordered the other way
 * round. Astral Sorcery compiles again in its own {@code postInit} from the same lists, so nothing is lost or
 * duplicated. If that is not possible the adapter is skipped instead; Modular Machinery reloads every adapter after
 * CraftTweaker has loaded its scripts, which is always after Astral Sorcery's {@code postInit}.</p>
 *
 * <p>Requires zero edits to either mod, is inert unless both are installed (see {@link CompatMixinPlugin}), and is only
 * needed for the Nova Engineering Core releases that still read the compiled array this way.</p>
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
