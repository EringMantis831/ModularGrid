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
 *
 * <p>Modular Machinery lets other mods register recipe adapters and loads them during its {@code postInit}: every
 * adapter's {@code createRecipesFor} runs inside mod loading, and an exception thrown there aborts the whole phase -
 * FML then reports Modular Machinery as the failing mod and no later mod gets its {@code postInit}. That is exactly how
 * a third-party adapter reading a neighbour mod's not-yet-built state (Nova Engineering Core's starlight infuser
 * adapter and Astral Sorcery's infusion recipes, for instance) turns one mod's bug into an unstartable pack.</p>
 *
 * <p>Modular Machinery already treats "this adapter produced nothing" as a normal, logged outcome
 * ({@code Adapter with name ... didn't provide have any recipes!}) and re-runs every adapter after CraftTweaker has
 * loaded its scripts, so a skipped adapter is not a dead end: transient causes fix themselves on that second pass.</p>
 *
 * <p>{@link Exception} and {@link LinkageError} are caught on purpose. The former covers an adapter that trips over
 * bad data, the latter the "compiled against another version" family ({@code NoSuchMethodError},
 * {@code NoClassDefFoundError}, {@code IncompatibleClassChangeError}) that an optional-neighbour adapter hits when the
 * neighbour it was built for is a different version. Anything else - a {@code StackOverflowError} or an
 * {@code OutOfMemoryError} - still propagates, because that is not an adapter-local problem.</p>
 *
 * <p>Public and outside {@code com.modulargrid.mixin} on purpose: this class is called from
 * {@code RecipeAdapterIsolationMixin}, whose body Mixin copies into Modular Machinery's
 * {@code RecipeLoader} - and Mixin refuses any direct reference to classes of a declared mixin package.</p>
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
