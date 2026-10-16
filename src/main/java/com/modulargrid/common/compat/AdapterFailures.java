package com.modulargrid.common.compat;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapterAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs one recipe adapter and keeps its failures to itself.
 */
public final class AdapterFailures {

    private static final Logger LOGGER = LogManager.getLogger("ModularGrid");

    private AdapterFailures() {
    }

    public static List<MachineRecipe> loadSafely(RecipeAdapterAccessor accessor, Supplier<List<MachineRecipe>> load) {
        String adapterKey = adapterKeyOf(accessor);
        try {
            List<MachineRecipe> loaded = load.get();
            if (loaded == null) {
                LOGGER.error("Recipe adapter '{}' returned null instead of a recipe list; treating it as empty", adapterKey);
                return Collections.emptyList();
            }
            return loaded;
        } catch (Exception | LinkageError failure) {
            LOGGER.error("Recipe adapter '{}' failed and was skipped. Modular Machinery runs every adapter again after "
                    + "CraftTweaker loads its scripts, so the recipes it provides can still appear", adapterKey, failure);
            return Collections.emptyList();
        }
    }

    private static String adapterKeyOf(RecipeAdapterAccessor accessor) {
        try {
            return accessor == null ? "unknown" : String.valueOf(accessor.getAdapterKey());
        } catch (RuntimeException failure) {
            return "unknown";
        }
    }
}
