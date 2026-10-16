package com.modulargrid.common.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Astral Sorcery's infusion recipe arrays, reached reflectively.
 *
 * <p>Astral Sorcery keeps two lists while its recipes are being registered and only folds them into the arrays its
 * own {@code InfusionRecipeRegistry#getRecipe(int)} reads during {@code CommonProxy#postInit}. Nova Engineering Core's
 * starlight infuser adapter reads those arrays, but Modular Machinery generates adapter recipes from <em>its</em>
 * {@code postInit}, which may run before Astral Sorcery's - that is what makes the adapter throw a
 * {@code NullPointerException} on {@code compiledRecipes}.</p>
 *
 * <p>Both lists are final by then: every mod's {@code init} has already run, and Astral Sorcery itself uses
 * {@code CraftingAccessManager#hasCompletedSetup()} as the "arrays exist" flag. Compiling early therefore only moves
 * work that Astral Sorcery would do a few moments later in its own {@code postInit} (which rebuilds the same arrays
 * from the same lists), so the adapter can keep its original behaviour instead of being skipped.</p>
 *
 * <p>Nothing here is linked at compile time; the classes are looked up by name so ModularGrid keeps working - and the
 * mixin stays inert - when either mod is absent.</p>
 *
 * <p>Public and outside {@code com.modulargrid.mixin} on purpose: this class is called from
 * {@code NovaEngStarlightAdapterMixin}, whose body Mixin copies into Nova Engineering Core's adapter - and Mixin
 * refuses any direct reference to classes of a declared mixin package.</p>
 */
public final class AstralRecipes {

    private static final String RECIPE_REGISTRY =
            "hellfirepvp.astralsorcery.common.crafting.infusion.InfusionRecipeRegistry";
    private static final String ACCESS_MANAGER =
            "hellfirepvp.astralsorcery.common.crafting.helper.CraftingAccessManager";

    private static boolean loggedFailure;
    private static boolean loggedEarlyCompile;

    private AstralRecipes() {
    }

    /**
     * Makes sure Astral Sorcery's recipe arrays exist.
     *
     * @return true when {@code InfusionRecipeRegistry#getRecipe(int)} can be used, false when the caller has to fall
     *         back to deferring its work
     */
    public static boolean ensureCompiled() {
        try {
            ClassLoader loader = AstralRecipes.class.getClassLoader();
            Class<?> manager = Class.forName(ACCESS_MANAGER, false, loader);
            Method hasCompletedSetup = manager.getMethod("hasCompletedSetup");
            if (isCompiled(hasCompletedSetup)) {
                return true;
            }

            manager.getMethod("compile").invoke(null);
            boolean compiled = isCompiled(hasCompletedSetup);
            if (compiled) {
                logEarlyCompile();
            }
            return compiled;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // Astral Sorcery is missing, or its recipe registry changed shape: the adapter has to be deferred instead.
            logFailure(failure);
            return false;
        }
    }

    private static boolean isCompiled(Method hasCompletedSetup) throws ReflectiveOperationException {
        Object completed = hasCompletedSetup.invoke(null);
        return completed instanceof Boolean && (Boolean) completed;
    }

    private static void logEarlyCompile() {
        if (loggedEarlyCompile) {
            return;
        }
        loggedEarlyCompile = true;
        LogManager.getLogger("ModularGrid").info(
                "Compiled Astral Sorcery's infusion recipes early so Nova Engineering Core's starlight infuser adapter "
                        + "can read them");
    }

    private static void logFailure(Throwable failure) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        LogManager.getLogger("ModularGrid").warn(
                "Could not compile Astral Sorcery's infusion recipes early; "
                        + "Nova Engineering Core's starlight infuser adapter will be deferred instead",
                failure);
    }
}
