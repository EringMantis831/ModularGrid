package com.modulargrid.common.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Astral Sorcery's infusion recipe arrays, reached reflectively.
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
